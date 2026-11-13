package com.taller.mecanica.service;

import com.taller.mecanica.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> EXTENSIONES_IMAGEN = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> EXTENSIONES_PDF = Set.of("pdf");

    private final Path raiz;

    public FileStorageService(@Value("${app.uploads-dir:uploads}") String uploadsDir) {
        this.raiz = Paths.get(uploadsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(raiz.resolve("evidencias"));
            Files.createDirectories(raiz.resolve("fichas-tecnicas"));
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear la carpeta de uploads: " + raiz, ex);
        }
    }

    public String guardarEvidencia(Long informeId, MultipartFile archivo) {
        return guardar("evidencias/" + informeId, archivo, EXTENSIONES_IMAGEN, "imagen");
    }

    public String guardarFichaTecnica(String ambito, Long propietarioId, MultipartFile archivo) {
        return guardar("fichas-tecnicas/" + ambito + "/" + propietarioId, archivo, EXTENSIONES_PDF, "pdf");
    }

    private String guardar(String subcarpeta, MultipartFile archivo, Set<String> extensionesPermitidas, String tipo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("El archivo esta vacio");
        }
        String original = StringUtils.cleanPath(Objects.requireNonNullElse(archivo.getOriginalFilename(), "archivo"));
        if (original.contains("..")) {
            throw new BusinessException("Nombre de archivo invalido");
        }
        int punto = original.lastIndexOf('.');
        if (punto < 0) {
            throw new BusinessException("El archivo debe tener una extension valida");
        }
        String extension = original.substring(punto + 1).toLowerCase(Locale.ROOT);
        if (!extensionesPermitidas.contains(extension)) {
            throw new BusinessException("Extension no permitida. Se aceptan: " + String.join(", ", extensionesPermitidas));
        }
        String contentType = Objects.requireNonNullElse(archivo.getContentType(), "");
        boolean tipoValido = "imagen".equals(tipo)
                ? contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                : contentType.toLowerCase(Locale.ROOT).startsWith("application/pdf") || contentType.isBlank();
        if (!tipoValido) {
            throw new BusinessException("El tipo de contenido del archivo no corresponde a un " + tipo);
        }

        Path directorio = raiz.resolve(subcarpeta).normalize();
        if (!directorio.startsWith(raiz)) {
            throw new BusinessException("Ruta de almacenamiento invalida");
        }
        String nombre = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path temporal = null;
        String url = "/uploads/" + subcarpeta + "/" + nombre;
        try {
            Files.createDirectories(directorio);
            temporal = Files.createTempFile(raiz, ".carga-", ".tmp");
            try (InputStream entrada = archivo.getInputStream()) {
                Files.copy(entrada, temporal, StandardCopyOption.REPLACE_EXISTING);
            }
            validarContenido(temporal, extension);
            Files.move(temporal, directorio.resolve(nombre), StandardCopyOption.ATOMIC_MOVE);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int estado) {
                        if (estado != STATUS_COMMITTED) eliminar(url);
                    }
                });
            }
            return url;
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer o guardar el archivo");
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ex) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("No se pudo borrar una carga temporal", ex);
                }
            }
        }
    }

    private void validarContenido(Path archivo, String extension) throws IOException {
        switch (extension) {
            case "pdf" -> validarPdf(archivo);
            case "webp" -> validarWebp(archivo);
            default -> validarImagen(archivo, extension.equals("png") ? "png" : "jpeg");
        }
    }

    private void validarImagen(Path archivo, String formato) throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(archivo.toFile())) {
            var lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) throw new BusinessException("El contenido no es una imagen valida");
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                if (!lector.getFormatName().equalsIgnoreCase(formato)) {
                    throw new BusinessException("La imagen no coincide con la extension");
                }
                validarDimensiones(lector.getWidth(0), lector.getHeight(0));
                lector.read(0);
            } finally {
                lector.dispose();
            }
        }
    }

    private void validarWebp(Path archivo) throws IOException {
        try (RandomAccessFile entrada = new RandomAccessFile(archivo.toFile(), "r")) {
            if (!leerTexto(entrada, 4).equals("RIFF")
                    || leerUInt32(entrada) != entrada.length() - 8 || !leerTexto(entrada, 4).equals("WEBP")) {
                throw new BusinessException("El contenido no es un WebP valido");
            }
            boolean imagen = false;
            while (entrada.getFilePointer() + 8 <= entrada.length()) {
                String tipo = leerTexto(entrada, 4);
                long longitud = leerUInt32(entrada);
                long fin = entrada.getFilePointer() + longitud + longitud % 2;
                if (fin > entrada.length()) throw new BusinessException("El WebP esta incompleto");
                if (tipo.equals("VP8 ") && longitud > 10) {
                    entrada.skipBytes(3);
                    if (entrada.readUnsignedByte() != 0x9d || entrada.readUnsignedByte() != 1
                            || entrada.readUnsignedByte() != 0x2a) throw new BusinessException("WebP invalido");
                    validarDimensiones(Short.reverseBytes(entrada.readShort()) & 0x3fff,
                            Short.reverseBytes(entrada.readShort()) & 0x3fff);
                    imagen = true;
                } else if (tipo.equals("VP8L") && longitud > 5) {
                    if (entrada.readUnsignedByte() != 0x2f) throw new BusinessException("WebP invalido");
                    long dimensiones = leerUInt32(entrada);
                    validarDimensiones((int) (dimensiones & 0x3fff) + 1,
                            (int) ((dimensiones >> 14) & 0x3fff) + 1);
                    imagen = true;
                } else if (tipo.equals("VP8X") && longitud == 10) {
                    entrada.skipBytes(4);
                    int ancho = entrada.readUnsignedByte() | entrada.readUnsignedByte() << 8 | entrada.readUnsignedByte() << 16;
                    int alto = entrada.readUnsignedByte() | entrada.readUnsignedByte() << 8 | entrada.readUnsignedByte() << 16;
                    validarDimensiones(ancho + 1, alto + 1);
                } else if (tipo.equals("ANMF") && longitud > 24) {
                    imagen = true;
                }
                entrada.seek(fin);
            }
            if (!imagen || entrada.getFilePointer() != entrada.length()) {
                throw new BusinessException("El WebP no contiene una imagen valida");
            }
        }
    }

    private void validarDimensiones(int ancho, int alto) {
        if (ancho <= 0 || alto <= 0 || (long) ancho * alto > 25_000_000) {
            throw new BusinessException("La imagen supera el limite de 25 megapixeles o tiene dimensiones invalidas");
        }
    }

    private void validarPdf(Path archivo) throws IOException {
        try (RandomAccessFile entrada = new RandomAccessFile(archivo.toFile(), "r")) {
            if (!leerTexto(entrada, 5).equals("%PDF-")) throw new BusinessException("El contenido no es un PDF valido");
            int longitud = (int) Math.min(2048, entrada.length());
            entrada.seek(entrada.length() - longitud);
            String finalPdf = leerTexto(entrada, longitud).stripTrailing();
            if (!finalPdf.matches("(?s).*startxref\\s+\\d+\\s+%%EOF")) {
                throw new BusinessException("El PDF esta incompleto o no tiene un indice valido");
            }
        }
    }

    private static String leerTexto(RandomAccessFile entrada, int longitud) throws IOException {
        byte[] bytes = new byte[longitud];
        entrada.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static long leerUInt32(RandomAccessFile entrada) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(entrada.readInt()));
    }

    public void eliminarDespuesDeConfirmar(String url) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eliminar(url);
            }
        });
    }

    public void eliminar(String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            return;
        }
        Path archivo = raiz.resolve(url.substring("/uploads/".length())).normalize();
        if (!archivo.startsWith(raiz)) {
            return;
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignored) {
            // El archivo fisico ya no existe o esta bloqueado: no se interrumpe la operacion.
        }
    }
}
