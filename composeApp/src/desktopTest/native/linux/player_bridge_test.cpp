#include "../../../desktopMain/native/linux/player_bridge.cpp"
#include <cassert>
#include <chrono>

int main() {
    Player player;
    player.lastSnapshotUs = G_USEC_PER_SEC;
    const auto now = player.lastSnapshotUs + 33'000;
    assert(shouldThrottleSnapshot(player, now, true, false));
    assert(!shouldThrottleSnapshot(player, now, false, false));
    assert(!shouldThrottleSnapshot(player, now, true, true));
    assert(!shouldThrottleSnapshot(player, now + G_USEC_PER_SEC / 2, true, false));
    player.overlayInteractiveUntilUs = now + G_USEC_PER_SEC;
    assert(!shouldThrottleSnapshot(player, now, true, false));
    player.overlayInteractiveUntilUs = 0;
    player.fadeTicks = 1;
    assert(!shouldThrottleSnapshot(player, now, true, false));
    player.fadeTicks = 0;
    player.snapInFlight = true;
    assert(!shouldThrottleSnapshot(player, now, true, false)); // watchdog must still run
    player.snapInFlight = false;
    player.nextEpisodeShown = true;
    assert(!shouldThrottleSnapshot(player, now, true, false));

    // Real mpv, local missing file only: failures must leave the loading state.
    player.mpv = mpv_create();
    assert(player.mpv);
    mpv_set_option_string(player.mpv, "vo", "null");
    mpv_set_option_string(player.mpv, "ao", "null");
    mpv_set_option_string(player.mpv, "idle", "yes");
    assert(mpv_initialize(player.mpv) >= 0);
    assert(playerLoading(&player));
    player.running.store(true);
    player.eventThread = std::thread(runEventLoop, &player);
    const char *cmd[] = {"loadfile", "/anivio-test-missing-directory/does-not-exist.mp4", nullptr};
    assert(mpv_command(player.mpv, cmd) >= 0);
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(5);
    while (!player.playbackFailed.load() && std::chrono::steady_clock::now() < deadline) {
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    assert(player.playbackFailed.load());
    assert(!playerLoading(&player));
    assert(!player.ended.load()); // a failed source must not trigger next episode
    player.running.store(false);
    mpv_wakeup(player.mpv);
    player.eventThread.join();
    mpv_terminate_destroy(player.mpv);
}
