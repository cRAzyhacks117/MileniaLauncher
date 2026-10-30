import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;

public final class MileniaLauncher extends JFrame
{
	private static final long serialVersionUID = 1L;
	private static final String UPDATES_URL = "https://milenia.onthewifi.com/updates/";
	private static final String CLIENT_ASSETS_URL = UPDATES_URL + "files/";
	private static final String PUBLIC_KEY_DER_BASE64 = "MCowBQYDK2VwAyEAKDI9uAz3dUj2NAP1x0Ezj8h7/JPHEYTVST0cox2Opb4=";
	private static final String CLIENT_EXE = "system/L2.exe";
	private static final String VERSION_FILE = ".milenia-version";
	private static final String APPLY_UPDATE_ARGUMENT = "--apply-update";
	private static final String SHORTCUT_NAME = "Milenia.lnk";
	private static final Color BACKGROUND = new Color(13, 15, 20);
	private static final Color PANEL = new Color(22, 25, 32);
	private static final Color GOLD = new Color(212, 175, 55);
	private static final Color TEXT = new Color(232, 228, 218);
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private final JTextField clientPath = new JTextField();
	private final JLabel status = new JLabel("Checking Milenia updates...");
	private final JProgressBar progress = new JProgressBar(0, 100);
	private final JButton checkButton = new JButton("Check for updates");
	private final JButton launchButton = new JButton("Launch Milenia");
	private transient SwingWorker<?, ?> activeWorker;
	private transient Path installedClient;
	private transient String expectedExecutableHash;
	private final boolean applyUpdateOnStartup;

	private MileniaLauncher(boolean applyUpdateOnStartup)
	{
		super("Milenia Launcher");
		this.applyUpdateOnStartup = applyUpdateOnStartup;
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setMinimumSize(new Dimension(620, 370));
		setSize(700, 420);
		setLocationRelativeTo(null);
		getContentPane().setBackground(BACKGROUND);
		setLayout(new BorderLayout(0, 18));
		add(createHeader(), BorderLayout.NORTH);
		add(createContent(), BorderLayout.CENTER);
		add(createFooter(), BorderLayout.SOUTH);
		loadClientPath();

		checkButton.addActionListener(event -> checkForUpdates());
		launchButton.addActionListener(event -> launchClient());
		SwingUtilities.invokeLater(this::checkForUpdates);
	}

	private JPanel createHeader()
	{
		JPanel header = new JPanel(new BorderLayout(16, 0));
		header.setBackground(PANEL);
		header.setBorder(BorderFactory.createEmptyBorder(22, 28, 20, 28));

		JLabel mark = new JLabel("M", SwingConstants.CENTER);
		mark.setPreferredSize(new Dimension(62, 62));
		mark.setOpaque(true);
		mark.setBackground(new Color(10, 12, 17));
		mark.setForeground(GOLD);
		mark.setFont(new Font(Font.SERIF, Font.BOLD, 42));
		mark.setBorder(BorderFactory.createLineBorder(new Color(121, 97, 45), 2));
		header.add(mark, BorderLayout.WEST);

		JPanel title = new JPanel(new GridBagLayout());
		title.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(0, 0, 4, 0);
		JLabel name = new JLabel("MILENIA");
		name.setForeground(TEXT);
		name.setFont(new Font(Font.SERIF, Font.BOLD, 25));
		title.add(name, c);
		c.gridy = 1;
		JLabel subtitle = new JLabel("INTERLUDE REALM");
		subtitle.setForeground(GOLD);
		subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		title.add(subtitle, c);
		header.add(title, BorderLayout.CENTER);
		return header;
	}

	private JPanel createContent()
	{
		JPanel content = new JPanel(new GridBagLayout());
		content.setOpaque(false);
		content.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 28));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(0, 0, 8, 0);
		JLabel folderLabel = new JLabel("Lineage II client folder");
		folderLabel.setForeground(TEXT);
		content.add(folderLabel, c);

		c.gridy++;
		c.insets = new Insets(0, 0, 18, 0);
		JPanel folderRow = new JPanel(new BorderLayout(8, 0));
		folderRow.setOpaque(false);
		clientPath.setEditable(false);
		folderRow.add(clientPath, BorderLayout.CENTER);
		JButton browse = new JButton("Browse...");
		browse.addActionListener(event -> chooseClientFolder());
		folderRow.add(browse, BorderLayout.EAST);
		content.add(folderRow, c);

		c.gridy++;
		c.insets = new Insets(0, 0, 10, 0);
		status.setForeground(new Color(190, 185, 174));
		content.add(status, c);

		c.gridy++;
		c.insets = new Insets(0, 0, 0, 0);
		progress.setStringPainted(true);
		progress.setString("");
		progress.setForeground(GOLD);
		progress.setBackground(PANEL);
		progress.setBorderPainted(false);
		content.add(progress, c);
		return content;
	}

	private JPanel createFooter()
	{
		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);
		footer.setBorder(BorderFactory.createEmptyBorder(0, 28, 24, 28));
		JPanel buttons = new JPanel(new BorderLayout(10, 0));
		buttons.setOpaque(false);
		checkButton.setForeground(TEXT);
		checkButton.setBackground(PANEL);
		launchButton.setForeground(new Color(20, 19, 15));
		launchButton.setBackground(GOLD);
		launchButton.setFont(launchButton.getFont().deriveFont(Font.BOLD));
		launchButton.setEnabled(false);
		buttons.add(checkButton, BorderLayout.WEST);
		buttons.add(launchButton, BorderLayout.EAST);
		footer.add(buttons, BorderLayout.SOUTH);
		return footer;
	}

	private void loadClientPath()
	{
		Path saved = settingsPath();
		try
		{
			if (Files.isRegularFile(saved))
			{
				String value = Files.readString(saved, StandardCharsets.UTF_8).trim();
				if (!value.isEmpty())
				{
					Path savedPath = Path.of(value).toAbsolutePath().normalize();
					if (!isUnsafeInstallPath(savedPath))
					{
						if (isLegacyDefaultClientPath(savedPath))
						{
							clientPath.setText(defaultClientPath().toString());
							return;
						}
						clientPath.setText(savedPath.toString());
						return;
					}
				}
			}
		}
		catch (IOException e)
		{
			showError("Could not read the saved client folder: " + e.getMessage(), e);
		}

		clientPath.setText(defaultClientPath().toString());
	}

	private static Path defaultClientPath()
	{
		return Path.of("C:\\Program Files\\Lineage II");
	}

	private static Path desktopPath()
	{
		return Path.of(System.getProperty("user.home"), "Desktop").toAbsolutePath().normalize();
	}

	private static boolean isLegacyDefaultClientPath(Path path)
	{
		Path normalized = path.toAbsolutePath().normalize();
		if (normalized.equals(desktopPath().resolve("Milenia")) ||
			normalized.equals(Path.of("C:\\Program Files\\Milenia")) ||
			normalized.equals(Path.of("C:\\Lineage II Milenia")))
		{
			return true;
		}
		String localAppData = System.getenv("LOCALAPPDATA");
		Path previousDefault = ((localAppData == null) || localAppData.isBlank())
			? Path.of(System.getProperty("user.home"), "AppData", "Local", "Milenia", "Lineage II")
			: Path.of(localAppData, "Milenia", "Lineage II");
		return normalized.equals(previousDefault.toAbsolutePath().normalize());
	}

	private static boolean isUnsafeInstallPath(Path path)
	{
		Path normalized = path.toAbsolutePath().normalize();
		if ((normalized.getRoot() != null) && normalized.equals(normalized.getRoot()))
		{
			return true;
		}
		if (normalized.equals(desktopPath()))
		{
			return true;
		}
		return false;
	}

	private static boolean isUnderDirectory(Path path, String directory)
	{
		return (directory != null) && !directory.isBlank() &&
			path.startsWith(Path.of(directory).toAbsolutePath().normalize());
	}

	private void chooseClientFolder()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select your Lineage II client folder");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		Path current = Path.of(clientPath.getText());
		if (Files.isDirectory(current))
		{
			chooser.setCurrentDirectory(current.toFile());
		}
		else
		{
			chooser.setCurrentDirectory(FileSystemView.getFileSystemView().getDefaultDirectory());
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			clientPath.setText(chooser.getSelectedFile().getAbsolutePath());
			checkForUpdates();
		}
	}

	private void checkForUpdates()
	{
		if (activeWorker != null && !activeWorker.isDone())
		{
			return;
		}

		launchButton.setEnabled(false);
		checkButton.setEnabled(false);
		progress.setValue(0);
		progress.setString("");
		status.setText("Checking the signed Milenia update manifest...");

		activeWorker = new SwingWorker<UpdatePlan, String>()
		{
			@Override
			protected UpdatePlan doInBackground() throws Exception
			{
				if (clientPath.getText().trim().isEmpty())
				{
					throw new IOException("Select a client folder before checking for updates.");
				}
				Path selectedClient = Path.of(clientPath.getText().trim()).toAbsolutePath().normalize();
				installedClient = isUnsafeInstallPath(selectedClient) ? defaultClientPath() : selectedClient;
				if (!installedClient.equals(selectedClient))
				{
					SwingUtilities.invokeLater(() -> clientPath.setText(installedClient.toString()));
				}
				if (Files.exists(installedClient) && !Files.isDirectory(installedClient))
				{
					throw new IOException("The selected client path is not a folder.");
				}
				saveClientPath(installedClient);

				Manifest manifest = downloadManifest();
				expectedExecutableHash = manifest.executableSha256;
				Path versionPath = installedClient.resolve(VERSION_FILE);
				long installedVersion = 0;
				if (Files.isRegularFile(versionPath))
				{
					try
					{
						installedVersion = Long.parseLong(Files.readString(versionPath, StandardCharsets.US_ASCII).trim());
					}
					catch (NumberFormatException e)
					{
						throw new IOException("The local Milenia version marker is invalid.", e);
					}
				}
				if (installedVersion > manifest.version)
				{
					throw new IOException("This client is newer than the version published by Milenia. Contact the server administrator.");
				}
				Path gameExecutable = resolveClientPath(installedClient, CLIENT_EXE);
				if ((installedVersion == manifest.version) && Files.isRegularFile(gameExecutable) &&
					sha256(gameExecutable).equals(manifest.executableSha256))
				{
					return new UpdatePlan(installedClient, manifest, List.of(), 0, 0, false);
				}

				List<FileEntry> changes = new ArrayList<>();
				long totalBytes = 0;
				int replacements = 0;
				for (int i = 0; i < manifest.files.size(); i++)
				{
					FileEntry entry = manifest.files.get(i);
					Path destination = resolveClientPath(installedClient, entry.path);
					boolean current = Files.isRegularFile(destination) &&
						(Files.size(destination) == entry.size) &&
						sha256(destination).equals(entry.sha256);
					if (!current)
					{
						changes.add(entry);
						totalBytes = Math.addExact(totalBytes, entry.size);
						if (Files.exists(destination))
						{
							replacements++;
						}
					}
					if ((i % 10) == 0)
					{
						publish("Checking client files (" + (i + 1) + "/" + manifest.files.size() + ")...");
						setProgress(5 + (int) (((long) (i + 1) * 40) / manifest.files.size()));
					}
				}
				return new UpdatePlan(installedClient, manifest, changes, totalBytes, replacements, installedVersion != manifest.version);
			}

			@Override
			protected void process(List<String> messages)
			{
				if (!messages.isEmpty())
				{
					status.setText(messages.get(messages.size() - 1));
				}
			}

			@Override
			protected void done()
			{
				try
				{
					UpdatePlan plan = get();
					checkButton.setEnabled(true);
					if (plan.files.isEmpty())
					{
						if (plan.writeVersionMarker && !isUnderProgramFiles(plan.root))
						{
							try
							{
								writeVersion(plan.root, plan.manifest.version);
							}
							catch (IOException e)
							{
								showError("Could not save the Milenia version marker: " + accessGuidance(e), e);
								return;
							}
						}
						setReady("Milenia is up to date (version " + plan.manifest.version + ").");
						launchClient();
						return;
					}

					if (applyUpdateOnStartup)
					{
						applyUpdate(plan);
						return;
					}

					String message = plan.files.size() + " client files need to be installed or updated.\n" +
						plan.replacements + " existing files will be replaced. Existing changed files are backed up in .milenia-backup.\n" +
						"Download size: " + formatBytes(plan.totalBytes) + "\n\nContinue?";
					int choice = JOptionPane.showConfirmDialog(
						MileniaLauncher.this,
						message,
						"Confirm Milenia client update",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE);
					if (choice == JOptionPane.YES_OPTION)
					{
						if (isUnderProgramFiles(plan.root))
						{
							requestElevatedUpdate();
						}
						else
						{
							applyUpdate(plan);
						}
					}

					else
					{
						status.setText("Update canceled. The game will not launch until the update is applied.");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					showError("The update check was interrupted.", e);
				}
				catch (ExecutionException e)
				{
					showError("Could not check for Milenia updates: " + accessGuidance(e), e);
				}
			}
		};
		activeWorker.addPropertyChangeListener(event -> {
			if ("progress".equals(event.getPropertyName()))
			{
				progress.setValue((Integer) event.getNewValue());
			}
		});
		activeWorker.execute();
	}

	private void requestElevatedUpdate()
	{
		try
		{
			Path executable = currentLauncherExecutable();
			String systemRoot = System.getenv("SystemRoot");
			if (systemRoot == null)
			{
				throw new IOException("Could not locate Windows PowerShell to request update permissions.");
			}
			Path powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
			ProcessBuilder builder = new ProcessBuilder(
				powershell.toString(),
				"-NoProfile",
				"-NonInteractive",
				"-Command",
				"try { Start-Process -FilePath $env:MILENIA_LAUNCHER_EXE -ArgumentList '--apply-update' -Verb RunAs -ErrorAction Stop; exit 0 } catch { [Console]::Error.WriteLine($_.Exception.Message); exit 1 }");
			builder.redirectErrorStream(true);
			builder.environment().put("MILENIA_LAUNCHER_EXE", executable.toString());
			Process process = builder.start();
			byte[] output;
			try (InputStream stream = process.getInputStream(); ByteArrayOutputStream captured = new ByteArrayOutputStream())
			{
				stream.transferTo(captured);
				output = captured.toByteArray();
			}
			int exitCode = process.waitFor();
			if (exitCode != 0)
			{
				throw new IOException(new String(output, StandardCharsets.UTF_8).trim());
			}
			status.setText("Starting the update with administrator permission...");
			dispose();
			System.exit(0);
		}
		catch (IOException e)
		{
			showError("Windows could not request permission to update Lineage II: " + e.getMessage(), e);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			showError("The administrator permission request was interrupted.", e);
		}
	}

	private static boolean isUnderProgramFiles(Path path)
	{
		String programFiles = System.getenv("ProgramFiles");
		String programFilesX86 = System.getenv("ProgramFiles(x86)");
		return isUnderDirectory(path.toAbsolutePath().normalize(), programFiles) ||
			isUnderDirectory(path.toAbsolutePath().normalize(), programFilesX86) ||
			isUnderDirectory(path.toAbsolutePath().normalize(), "C:\\Program Files") ||
			isUnderDirectory(path.toAbsolutePath().normalize(), "C:\\Program Files (x86)");
	}

	private static Path currentLauncherExecutable() throws IOException
	{
		String command = ProcessHandle.current().info().command()
			.orElseThrow(() -> new IOException("Could not locate the running Milenia launcher executable."));
		Path executable = Path.of(command).toAbsolutePath().normalize();
		if (!"MileniaLauncher.exe".equalsIgnoreCase(executable.getFileName().toString()))
		{
			throw new IOException("The launcher is not running from its packaged Windows executable.");
		}
		return executable;
	}

	private static Path launcherInstallDirectory()
	{
		String localAppData = System.getenv("LOCALAPPDATA");
		Path base = ((localAppData == null) || localAppData.isBlank())
			? Path.of(System.getProperty("user.home"), "AppData", "Local")
			: Path.of(localAppData);
		return base.resolve("Milenia").resolve("Launcher");
	}

	private static void installLauncherIfNeeded(String[] args) throws IOException, InterruptedException
	{
		Path runningExecutable = currentLauncherExecutable();
		Path sourceRoot = runningExecutable.getParent();
		Path installedRoot = launcherInstallDirectory().toAbsolutePath().normalize();
		Path installedExecutable = installedRoot.resolve(runningExecutable.getFileName());
		boolean alreadyInstalled = sourceRoot.toAbsolutePath().normalize().equals(installedRoot);
		if (!alreadyInstalled)
		{
			Files.createDirectories(installedRoot);
			try (Stream<Path> paths = Files.walk(sourceRoot))
			{
				for (Path source : paths.toList())
				{
					Path normalizedSource = source.toAbsolutePath().normalize();
					if (normalizedSource.startsWith(installedRoot))
					{
						continue;
					}
					Path destination = installedRoot.resolve(sourceRoot.relativize(source));
					if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
					{
						Files.createDirectories(destination);
					}
					else if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
					{
						Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			if (!Files.isRegularFile(installedExecutable))
			{
				throw new IOException("The installed Milenia launcher executable is missing.");
			}
			createDesktopShortcut(installedExecutable, installedRoot);
			List<String> command = new ArrayList<>();
			command.add(installedExecutable.toString());
			command.addAll(List.of(args));
			new ProcessBuilder(command).start();
			return;
		}

		createDesktopShortcut(installedExecutable, installedRoot);
	}

	private static void createDesktopShortcut(Path executable, Path launcherRoot) throws IOException, InterruptedException
	{
		Path icon = launcherRoot.resolve("Milenia.ico");
		if (!Files.isRegularFile(icon))
		{
			writeLauncherIcon(icon);
		}

		String systemRoot = System.getenv("SystemRoot");
		if (systemRoot == null)
		{
			throw new IOException("Could not locate Windows PowerShell to create the Desktop shortcut.");
		}
		Path powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
		String command = "$ErrorActionPreference = 'Stop'; " +
			"$desktop = [Environment]::GetFolderPath('Desktop'); " +
			"if ([string]::IsNullOrWhiteSpace($desktop)) { throw 'Windows Desktop folder was not found.' }; " +
			"$shortcut = (New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path $desktop 'Milenia.lnk')); " +
			"$shortcut.TargetPath = $env:MILENIA_LAUNCHER_EXE; " +
			"$shortcut.WorkingDirectory = $env:MILENIA_LAUNCHER_DIR; " +
			"$shortcut.IconLocation = $env:MILENIA_LAUNCHER_ICON + ',0'; " +
			"$shortcut.Description = 'Launch Milenia Interlude'; " +
			"$shortcut.Save()";
		ProcessBuilder builder = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive", "-Command", command)
			.redirectErrorStream(true);
		builder.environment().put("MILENIA_LAUNCHER_EXE", executable.toString());
		builder.environment().put("MILENIA_LAUNCHER_DIR", launcherRoot.toString());
		builder.environment().put("MILENIA_LAUNCHER_ICON", icon.toString());
		Process process = builder.start();
		byte[] output;
		try (InputStream stream = process.getInputStream(); ByteArrayOutputStream captured = new ByteArrayOutputStream())
		{
			stream.transferTo(captured);
			output = captured.toByteArray();
		}
		int exitCode = process.waitFor();
		if (exitCode != 0)
		{
			String details = new String(output, StandardCharsets.UTF_8).trim();
			throw new IOException(details.isEmpty() ? "Windows could not create the Desktop shortcut." : details);
		}
	}

	private static void writeLauncherIcon(Path icon) throws IOException
	{
		BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new Color(10, 12, 17));
			graphics.fillRoundRect(8, 8, 240, 240, 42, 42);
			graphics.setColor(new Color(212, 175, 55));
			graphics.setStroke(new java.awt.BasicStroke(8));
			graphics.drawRoundRect(12, 12, 232, 232, 38, 38);
			graphics.setFont(new Font(Font.SERIF, Font.BOLD, 190));
			java.awt.FontMetrics metrics = graphics.getFontMetrics();
			String mark = "M";
			int x = (256 - metrics.stringWidth(mark)) / 2;
			int y = ((256 - metrics.getHeight()) / 2) + metrics.getAscent();
			graphics.drawString(mark, x, y);
		}
		finally
		{
			graphics.dispose();
		}

		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(image, "png", png);
		try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(icon, StandardOpenOption.CREATE_NEW)))
		{
			writeLittleEndianShort(output, 0);
			writeLittleEndianShort(output, 1);
			writeLittleEndianShort(output, 1);
			output.writeByte(0);
			output.writeByte(0);
			output.writeByte(0);
			output.writeByte(0);
			writeLittleEndianShort(output, 1);
			writeLittleEndianShort(output, 32);
			writeLittleEndianInt(output, png.size());
			writeLittleEndianInt(output, 22);
			png.writeTo(output);
		}
	}

	private static void writeLittleEndianShort(DataOutputStream output, int value) throws IOException
	{
		output.writeByte(value & 0xff);
		output.writeByte((value >>> 8) & 0xff);
	}

	private static void writeLittleEndianInt(DataOutputStream output, int value) throws IOException
	{
		output.writeByte(value & 0xff);
		output.writeByte((value >>> 8) & 0xff);
		output.writeByte((value >>> 16) & 0xff);
		output.writeByte((value >>> 24) & 0xff);
	}
	private void applyUpdate(UpdatePlan plan)
	{
		checkButton.setEnabled(false);
		launchButton.setEnabled(false);
		status.setText("Downloading verified client updates...");
		progress.setValue(45);

		activeWorker = new SwingWorker<Void, String>()
		{
			@Override
			protected Void doInBackground() throws Exception
			{
				for (int i = 0; i < plan.files.size(); i++)
				{
					FileEntry entry = plan.files.get(i);
					publish("Downloading " + (i + 1) + "/" + plan.files.size() + ": " + entry.path);
					installFile(plan.root, entry, plan.manifest.version);
					setProgress(45 + (int) (((long) (i + 1) * 55) / plan.files.size()));
				}
				writeVersion(plan.root, plan.manifest.version);
				return null;
			}

			@Override
			protected void process(List<String> messages)
			{
				if (!messages.isEmpty())
				{
					status.setText(messages.get(messages.size() - 1));
				}
			}

			@Override
			protected void done()
			{
				checkButton.setEnabled(true);
				try
				{
					get();
					setReady("Milenia client is ready (version " + plan.manifest.version + ").");
					launchClient();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					showError("The update was interrupted. Open the launcher again to finish.", e);
				}
				catch (ExecutionException e)
				{
					showError("The update did not complete: " + accessGuidance(e), e);
				}
			}
		};
		activeWorker.addPropertyChangeListener(event -> {
			if ("progress".equals(event.getPropertyName()))
			{
				progress.setValue((Integer) event.getNewValue());
			}
		});
		activeWorker.execute();
	}

	private Manifest downloadManifest() throws IOException, InterruptedException, GeneralSecurityException
	{
		byte[] bundle = getBytes(URI.create(UPDATES_URL + "manifest.bundle"), 4 * 1024 * 1024);
		String[] parts = new String(bundle, StandardCharsets.US_ASCII).trim().split("\\R", -1);
		if ((parts.length != 3) || !"MILENIA-MANIFEST-V1".equals(parts[0]))
		{
			throw new IOException("The update server returned an invalid manifest bundle.");
		}
		byte[] manifestBytes;
		byte[] signatureBytes;
		try
		{
			manifestBytes = Base64.getDecoder().decode(parts[1]);
			signatureBytes = Base64.getDecoder().decode(parts[2]);
		}
		catch (IllegalArgumentException e)
		{
			throw new IOException("The update manifest encoding is invalid.", e);
		}

		byte[] publicKeyBytes = Base64.getDecoder().decode(PUBLIC_KEY_DER_BASE64);
		PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
		Signature verifier = Signature.getInstance("Ed25519");
		verifier.initVerify(publicKey);
		verifier.update(manifestBytes);
		if (!verifier.verify(signatureBytes))
		{
			throw new GeneralSecurityException("The update manifest signature is invalid; no files were changed.");
		}
		return parseManifest(manifestBytes);
	}

	private Manifest parseManifest(byte[] bytes) throws IOException
	{
		Map<String, String> values = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isBlank())
				{
					continue;
				}
				int separator = line.indexOf('=');
				if (separator <= 0)
				{
					throw new IOException("The signed update manifest contains an invalid line.");
				}
				String key = line.substring(0, separator);
				if (values.putIfAbsent(key, line.substring(separator + 1)) != null)
				{
					throw new IOException("The signed update manifest contains a duplicate field: " + key);
				}
			}
		}

		if (!"1".equals(values.get("format")))
		{
			throw new IOException("This launcher does not support the update manifest format.");
		}
		long version = parsePositiveLong(values.get("version"), "version");
		int count;
		try
		{
			count = Integer.parseInt(values.get("file.count"));
		}
		catch (NumberFormatException e)
		{
			throw new IOException("The update manifest file count is invalid.", e);
		}
		if ((count < 1) || (count > 10000))
		{
			throw new IOException("The update manifest contains an unsupported number of files.");
		}

		String executableHash = requireHash(values.get("client.executable.sha256"), "client executable");
		List<FileEntry> files = new ArrayList<>(count);
		Set<String> paths = new HashSet<>();
		boolean executableIncluded = false;
		for (int i = 0; i < count; i++)
		{
			String prefix = "file." + i + ".";
			String path;
			try
			{
				path = new String(Base64.getUrlDecoder().decode(required(values, prefix + "path64")), StandardCharsets.UTF_8);
			}
			catch (IllegalArgumentException e)
			{
				throw new IOException("A client file path in the manifest is invalid.", e);
			}
			validateRelativePath(path);
			if (!paths.add(path.toLowerCase(Locale.ROOT)))
			{
				throw new IOException("The update manifest contains duplicate client paths.");
			}
			long size = parseNonNegativeLong(values.get(prefix + "size"), prefix + "size");
			String hash = requireHash(values.get(prefix + "sha256"), prefix + "sha256");
			if (CLIENT_EXE.equalsIgnoreCase(path))
			{
				executableIncluded = true;
				if (!executableHash.equals(hash))
				{
					throw new IOException("The manifest client executable hash is inconsistent.");
				}
			}
			files.add(new FileEntry(path, size, hash));
		}
		if (!executableIncluded)
		{
			throw new IOException("The signed update manifest does not include system/L2.exe.");
		}
		return new Manifest(version, executableHash, List.copyOf(files));
	}

	private static void validateRelativePath(String path) throws IOException
	{
		if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":") ||
			path.equalsIgnoreCase("LineageII.exe") || path.equalsIgnoreCase(VERSION_FILE))
		{
			throw new IOException("The update manifest contains a forbidden client path.");
		}
		for (String segment : path.split("/", -1))
		{
			if (segment.isBlank() || segment.equals(".") || segment.equals("..") ||
				segment.endsWith(".") || segment.endsWith(" ") ||
				segment.startsWith(".milenia-backup") || segment.startsWith(".milenia-staging"))
			{
				throw new IOException("The update manifest contains an unsafe client path.");
			}
		}
	}

	private static String required(Map<String, String> values, String key) throws IOException
	{
		String value = values.get(key);
		if (value == null)
		{
			throw new IOException("The update manifest is missing " + key + ".");
		}
		return value;
	}

	private static long parsePositiveLong(String value, String field) throws IOException
	{
		long parsed = parseNonNegativeLong(value, field);
		if (parsed == 0)
		{
			throw new IOException("The update manifest " + field + " must be positive.");
		}
		return parsed;
	}

	private static long parseNonNegativeLong(String value, String field) throws IOException
	{
		try
		{
			long parsed = Long.parseLong(value);
			if (parsed < 0)
			{
				throw new NumberFormatException();
			}
			return parsed;
		}
		catch (NumberFormatException e)
		{
			throw new IOException("The update manifest " + field + " is invalid.", e);
		}
	}

	private static String requireHash(String value, String field) throws IOException
	{
		if ((value == null) || !value.matches("[0-9a-f]{64}"))
		{
			throw new IOException("The update manifest " + field + " SHA-256 is invalid.");
		}
		return value;
	}

	private static byte[] getBytes(URI uri, int maxBytes) throws IOException, InterruptedException
	{
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(30))
			.header("User-Agent", "MileniaLauncher/1.0")
			.GET()
			.build();
		HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200)
		{
			response.body().close();
			throw new IOException("Update server returned HTTP " + response.statusCode() + " for " + uri.getPath());
		}
		try (InputStream input = response.body(); ByteArrayOutputStream output = new ByteArrayOutputStream())
		{
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = input.read(buffer)) != -1)
			{
				total += read;
				if (total > maxBytes)
				{
					throw new IOException("Update server returned an unexpectedly large manifest.");
				}
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		}
	}

	private void installFile(Path root, FileEntry entry, long version) throws IOException, InterruptedException
	{
		Path destination = resolveClientPath(root, entry.path);
		Files.createDirectories(destination.getParent());
		Path temporary = destination.resolveSibling(destination.getFileName() + ".milenia-download");
		try
		{
			downloadFile(entry, temporary);
			if (Files.exists(destination))
			{
				Path backup = resolveClientPath(root.resolve(".milenia-backup").resolve(Long.toString(version)), entry.path);
				if (!Files.exists(backup))
				{
					Files.createDirectories(backup.getParent());
					Files.copy(destination, backup);
				}
			}
			try
			{
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			Files.deleteIfExists(temporary);
		}
	}

	private void downloadFile(FileEntry entry, Path destination) throws IOException, InterruptedException
	{
		HttpRequest request = HttpRequest.newBuilder(clientFileUri(entry))
			.timeout(Duration.ofMinutes(30))
			.header("User-Agent", "MileniaLauncher/1.0")
			.GET()
			.build();
		HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200)
		{
			response.body().close();
			throw new IOException("Update server returned HTTP " + response.statusCode() + " for " + entry.path);
		}

		MessageDigest digest = sha256Digest();
		long total = 0;
		try (InputStream input = response.body();
			var output = Files.newOutputStream(destination))
		{
			byte[] buffer = new byte[128 * 1024];
			int read;
			while ((read = input.read(buffer)) != -1)
			{
				total = Math.addExact(total, read);
				if (total > entry.size)
				{
					throw new IOException("Downloaded file is larger than the signed manifest: " + entry.path);
				}
				digest.update(buffer, 0, read);
				output.write(buffer, 0, read);
			}
		}
		if ((total != entry.size) || !toHex(digest.digest()).equals(entry.sha256))
		{
			throw new IOException("Downloaded file failed size or SHA-256 verification: " + entry.path);
		}
	}

	private static URI clientFileUri(FileEntry entry)
	{
		return URI.create(CLIENT_ASSETS_URL + entry.sha256.substring(0, 2) + "/" + entry.sha256 + ".bin");
	}

	private static Path resolveClientPath(Path root, String relativePath) throws IOException
	{
		validateRelativePath(relativePath);
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path destination = normalizedRoot.resolve(relativePath.replace("/", java.io.File.separator)).normalize();
		if (!destination.startsWith(normalizedRoot))
		{
			throw new IOException("Client file path escapes the selected installation folder.");
		}
		Path parent = normalizedRoot;
		String[] segments = relativePath.split("/");
		for (int i = 0; i < (segments.length - 1); i++)
		{
			parent = parent.resolve(segments[i]);
			if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(parent))
			{
				throw new IOException("Client update path traverses a symbolic link.");
			}
		}
		return destination;
	}

	private static String sha256(Path path) throws IOException
	{
		MessageDigest digest = sha256Digest();
		try (InputStream input = Files.newInputStream(path))
		{
			byte[] buffer = new byte[128 * 1024];
			int read;
			while ((read = input.read(buffer)) != -1)
			{
				digest.update(buffer, 0, read);
			}
		}
		return toHex(digest.digest());
	}

	private static MessageDigest sha256Digest()
	{
		try
		{
			return MessageDigest.getInstance("SHA-256");
		}
		catch (GeneralSecurityException e)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}

	private static String toHex(byte[] bytes)
	{
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes)
		{
			result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
		}
		return result.toString();
	}

	private static void writeVersion(Path root, long version) throws IOException
	{
		Files.writeString(root.resolve(VERSION_FILE), Long.toString(version) + System.lineSeparator(), StandardCharsets.US_ASCII);
	}

	private void saveClientPath(Path path) throws IOException
	{
		Path settings = settingsPath();
		Files.createDirectories(settings.getParent());
		Files.writeString(settings, path.toString(), StandardCharsets.UTF_8);
	}

	private static Path settingsPath()
	{
		String appData = System.getenv("APPDATA");
		Path base = (appData == null) ? Path.of(System.getProperty("user.home")) : Path.of(appData);
		return base.resolve("Milenia").resolve("client-path.txt");
	}

	private void setReady(String message)
	{
		status.setText(message);
		progress.setValue(100);
		progress.setString("Ready");
		launchButton.setEnabled(true);
		checkButton.setEnabled(true);
	}

	private void launchClient()
	{
		Path executable = installedClient.resolve(CLIENT_EXE.replace("/", java.io.File.separator));
		if (!Files.isRegularFile(executable))
		{
			showError("The verified game executable is missing. Check for updates before launching.", null);
			launchButton.setEnabled(false);
			return;
		}
		try
		{
			if ((expectedExecutableHash == null) || !sha256(executable).equals(expectedExecutableHash))
			{
				showError("The game executable does not match the signed Milenia update. Check for updates before launching.", null);
				return;
			}
			String systemRoot = System.getenv("SystemRoot");
			if (systemRoot == null)
			{
				throw new IOException("Could not locate Windows PowerShell to request game permissions.");
			}
			Path powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
			if (!Files.isRegularFile(powershell))
			{
				throw new IOException("Windows PowerShell was not found at " + powershell + ".");
			}

			launchButton.setEnabled(false);
			checkButton.setEnabled(false);
			status.setText("Windows may ask permission to start Lineage II. Approve the prompt to play.");
			activeWorker = new SwingWorker<String, Void>()
			{
				@Override
				protected String doInBackground() throws Exception
				{
					String command = "$ErrorActionPreference = 'Stop'; try { " +
						"Start-Process -FilePath $env:MILENIA_GAME_EXE " +
						"-WorkingDirectory $env:MILENIA_GAME_DIR -Verb RunAs -ErrorAction Stop; exit 0 " +
						"} catch { [Console]::Error.WriteLine($_.Exception.Message); exit 1 }";
					ProcessBuilder builder = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive", "-Command", command)
						.redirectErrorStream(true);
					builder.environment().put("MILENIA_GAME_EXE", executable.toString());
					builder.environment().put("MILENIA_GAME_DIR", executable.getParent().toString());
					Process process = builder.start();
					byte[] output;
					try (InputStream stream = process.getInputStream(); ByteArrayOutputStream captured = new ByteArrayOutputStream())
					{
						stream.transferTo(captured);
						output = captured.toByteArray();
					}
					int exitCode = process.waitFor();
					if (exitCode != 0)
					{
						String details = new String(output, StandardCharsets.UTF_8).trim();
						throw new IOException(details.isEmpty()
							? "Windows could not start the game with administrator permissions."
							: details);
					}
					return new String(output, StandardCharsets.UTF_8).trim();
				}

				@Override
				protected void done()
				{
					checkButton.setEnabled(true);
					launchButton.setEnabled(true);
					try
					{
						get();
						status.setText("Game launched. If Windows displayed a permission prompt, approve it to continue.");
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						showError("The game launch was interrupted.", e);
					}
					catch (ExecutionException e)
					{
						showError("Windows could not start Lineage II. If you canceled the permission prompt, approve it and try again: " +
							rootMessage(e), e);
					}
				}
			};
			activeWorker.execute();
		}
		catch (IOException e)
		{
			showError("Could not prepare to start system\\L2.exe: " + e.getMessage(), e);
		}
	}

	private void showError(String message, Throwable error)
	{
		status.setText(message);
		launchButton.setEnabled(false);
		checkButton.setEnabled(true);
		progress.setString("Error");
		if (error != null)
		{
			try
			{
				Path log = settingsPath().getParent().resolve("launcher.log");
				Files.createDirectories(log.getParent());
				Files.writeString(log, error.toString() + System.lineSeparator(), StandardCharsets.UTF_8,
					java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			}
			catch (IOException logError)
			{
				message = message + " (Could not write launcher.log: " + logError.getMessage() + ")";
			}
		}
		JOptionPane.showMessageDialog(this, message, "Milenia Launcher", JOptionPane.ERROR_MESSAGE);
	}

	private static String rootMessage(Exception error)
	{
		Throwable cause = error;
		while ((cause.getCause() != null) && (cause.getCause() != cause))
		{
			cause = cause.getCause();
		}
		String message = cause.getMessage();
		return (message == null) ? cause.getClass().getSimpleName() : message;
	}

	private static String accessGuidance(Exception error)
	{
		String message = rootMessage(error);
		String lowerCase = message.toLowerCase(Locale.ROOT);
		if (lowerCase.contains("access is denied") || lowerCase.contains("access denied") || lowerCase.contains("permission"))
		{
			return message + " Updates to C:\\Program Files\\Lineage II require approving the Windows administrator prompt.";
		}
		return message;
	}

	private static String formatBytes(long bytes)
	{
		if (bytes >= (1024L * 1024 * 1024))
		{
			return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024 * 1024));
		}
		return String.format(Locale.ROOT, "%.0f MiB", bytes / (1024.0 * 1024));
	}

	private record FileEntry(String path, long size, String sha256)
	{
	}

	private record Manifest(long version, String executableSha256, List<FileEntry> files)
	{
	}

	private record UpdatePlan(Path root, Manifest manifest, List<FileEntry> files, long totalBytes, int replacements, boolean writeVersionMarker)
	{
	}

	public static void main(String[] args)
	{
		UIManager.put("OptionPane.background", PANEL);
		UIManager.put("Panel.background", PANEL);
		UIManager.put("OptionPane.messageForeground", TEXT);
		boolean applyUpdateOnStartup = List.of(args).contains(APPLY_UPDATE_ARGUMENT);
		try
		{
			installLauncherIfNeeded(args);
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(null, e.getMessage(), "Milenia Launcher", JOptionPane.ERROR_MESSAGE);
			return;
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			JOptionPane.showMessageDialog(null, "Launcher installation was interrupted.", "Milenia Launcher", JOptionPane.ERROR_MESSAGE);
			return;
		}
		SwingUtilities.invokeLater(() -> new MileniaLauncher(applyUpdateOnStartup).setVisible(true));
	}
}
