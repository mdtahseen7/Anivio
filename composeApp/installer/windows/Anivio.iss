[Setup]
AppId=Anivio.Setup
AppName=Anivio
AppVersion={#AppVersion}
AppPublisher=Anivio
AppPublisherURL=https://anivio.eu.cc/
AppSupportURL=https://github.com/mdtahseen7/Anivio/issues
VersionInfoVersion={#PackageVersion}
VersionInfoTextVersion={#AppVersion}
VersionInfoDescription=Anivio Windows Setup
SetupIconFile=..\..\src\desktopMain\resources\icons\app-icon-av-transparent.ico
WizardSmallImageFile=..\..\src\desktopMain\resources\icons\app-icon-av-transparent.png
WizardImageFile=
WizardBackImageFile=wallpaper.png
WizardBackColor=#111816
WizardBackImageOpacity=74
WizardStyle=modern dark hidebevels
WizardSizePercent=108,108
DisableWelcomePage=no
DisableReadyPage=yes
CreateAppDir=no
Uninstallable=no
CreateUninstallRegKey=no
CloseApplications=no
PrivilegesRequired=admin
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
OutputDir={#OutputPath}
OutputBaseFilename=Anivio-Windows-x64
Compression=none

[Messages]
WelcomeLabel1=Install Anivio
WelcomeLabel2=Set up [name/ver] for Windows.%n%nYour local profile and settings are kept when you upgrade.
FinishedHeadingLabel=Installation complete
FinishedLabel=You can open Anivio from the Start menu.

[Files]
Source: "{#Payload}"; DestName: "Anivio.msi"; Flags: dontcopy

[Code]
var
  RestartRequired: Boolean;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  PackagePath: String;
  ExitCode: Integer;
begin
  PackagePath := ExpandConstant('{tmp}\Anivio.msi');
  if not FileExists(PackagePath) then
    ExtractTemporaryFile('Anivio.msi');

  WizardForm.PreparingLabel.Caption := 'Installing Anivio. This can take a minute.';
  if not Exec(ExpandConstant('{sys}\msiexec.exe'),
      '/i "' + PackagePath + '" /qn /norestart', '', SW_HIDE,
      ewWaitUntilTerminated, ExitCode) then
    Result := 'Windows Installer could not start. Error ' + IntToStr(ExitCode) + '.'
  else if (ExitCode = 0) or (ExitCode = 3010) then
  begin
    RestartRequired := ExitCode = 3010;
    Result := '';
  end
  else
    Result := 'Windows Installer could not complete setup. Error ' +
      IntToStr(ExitCode) + '. Close Anivio and try again.';
end;

function NeedRestart: Boolean;
begin
  Result := RestartRequired;
end;
