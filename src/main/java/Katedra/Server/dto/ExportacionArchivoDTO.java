package Katedra.Server.dto;

/**
 * A rendered export ready to be written to the HTTP response.
 *
 * @param nombreArchivo download filename, already slugified (safe for a Content-Disposition header)
 * @param mediaType     MIME type matching the chosen format
 * @param contenido     the file bytes
 */
public record ExportacionArchivoDTO(String nombreArchivo, String mediaType, byte[] contenido) {}
