# Milenia Launcher

Windows updater and launcher for the Milenia Lineage II Interlude client.

The launcher checks the signed update manifest over HTTPS, verifies downloaded
client files by SHA-256, asks before updating, and starts `system\L2.exe`. It
does not launch the root `LineageII.exe`.

## Download and current signing status

Download: [MileniaLauncher.zip](https://milenia.onthewifi.com/updates/MileniaLauncher.zip)

The released launcher is currently unsigned. Windows Smart App Control may
block it as untrusted, and SmartScreen may show an unknown-publisher warning.
Do not disable Windows security protections to run it. The project is preparing
an application for free open-source code signing; signing is not active unless
and until the project is accepted and a signed release is published.

## Build

Requirements: Windows and a JDK 21 installation that includes `javac`, `jar`,
and `jpackage`.

Run the packaging script from PowerShell:

```powershell
.\build.ps1 -JdkBin 'C:\path\to\jdk-21\bin'
```

The script writes the packaged launcher to the project's `dist` directory.
The default JDK path is configured for the original Milenia build machine.

## Network and privacy

When opened, the launcher contacts the Milenia update website over HTTPS to
download the signed manifest. When the user confirms an update, it downloads
the required client files. The launcher does not send game account credentials
or client files. The website may log ordinary HTTP request metadata, such as IP
address, request path, and request time.

## License

The launcher source and build script are licensed under the MIT License. See
[LICENSE](LICENSE).

## Code signing policy

The project is applying for free code signing through SignPath Foundation.
This application is pending and is not an endorsement or approval. If accepted,
SignPath Foundation will be identified as the certificate publisher. Until a
trusted signed release is available, the launcher remains unsigned.

- Requested arrangement: "Free code signing provided by SignPath.io,
  certificate by SignPath Foundation" (pending approval).
- Committer, reviewer, and signing approver: Teo (`@cRAzyhacks117`).
- Source and release history: <https://github.com/cRAzyhacks117/MileniaLauncher>
- Privacy: This program does not transfer account credentials or client files.
  Update requests are made when the user opens the launcher or confirms an
  update; ordinary web server request metadata may be logged.
