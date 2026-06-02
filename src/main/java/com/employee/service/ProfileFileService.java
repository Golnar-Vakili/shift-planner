package com.employee.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;

@Service
public class ProfileFileService {

	private final Path baseDir;

	public ProfileFileService() {
		// Project root folder for uploaded profile images
		this.baseDir = Paths.get(System.getProperty("user.dir"), "user-photos").normalize().toAbsolutePath();
	}

	public String storeProfileImage(Long employeeId, MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IOException("Empty file");
		}
		Files.createDirectories(baseDir);

		String ext = guessExtension(file.getOriginalFilename(), file.getContentType());
		String filename = "user_" + employeeId + "_" + Instant.now().toEpochMilli() + ext;
		Path target = baseDir.resolve(filename).normalize().toAbsolutePath();
		if (!target.startsWith(baseDir)) {
			throw new IOException("Invalid path");
		}

		Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
		return filename;
	}

	public Resource loadAsResource(String filename) throws IOException {
		if (filename == null) throw new IOException("Missing filename");
		Path target = baseDir.resolve(filename).normalize().toAbsolutePath();
		if (!target.startsWith(baseDir)) throw new IOException("Invalid path");
		if (!Files.exists(target) || !Files.isReadable(target)) throw new IOException("Not found");
		try {
			return new UrlResource(target.toUri());
		} catch (MalformedURLException e) {
			throw new IOException("Bad URL", e);
		}
	}

	public Path getBaseDir() {
		return baseDir;
	}

	private static String guessExtension(String originalName, String contentType) {
		String byType = (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT));
		if (byType.contains("jpeg")) return ".jpg";
		if (byType.contains("png")) return ".png";
		if (byType.contains("webp")) return ".webp";

		String n = (originalName == null ? "" : originalName.trim());
		int dot = n.lastIndexOf('.');
		if (dot > -1 && dot < n.length() - 1) {
			String ext = n.substring(dot + 1).toLowerCase(Locale.ROOT);
			if (ext.equals("jpg") || ext.equals("jpeg")) return ".jpg";
			if (ext.equals("png")) return ".png";
			if (ext.equals("webp")) return ".webp";
		}
		return ".png";
	}
}

