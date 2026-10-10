package com.dotastats.meta;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Persistencia en JSON del snapshot de meta. Nunca propaga fallos de E/S: un archivo ilegible se ignora y
 * una escritura fallida solo se registra, porque la caché en disco es una optimización y no puede tumbar la app.
 */
@Component
public class CacheDisco {

    private static final Logger log = LoggerFactory.getLogger(CacheDisco.class);

    private final Path archivo;
    private final JsonMapper mapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Autowired
    public CacheDisco(MetaProperties props) {
        this(StringUtils.hasText(props.cacheFile()) ? Path.of(props.cacheFile()) : null);
    }

    /** @param archivo destino de la caché; null la deshabilita */
    CacheDisco(Path archivo) {
        this.archivo = archivo;
    }

    public boolean habilitada() {
        return archivo != null;
    }

    public Optional<SnapshotMeta> cargar() {
        if (archivo == null || !Files.isRegularFile(archivo)) {
            return Optional.empty();
        }
        try {
            SnapshotMeta snapshot = mapper.readValue(archivo.toFile(), SnapshotMeta.class);
            if (!esValido(snapshot)) {
                log.warn("La caché en disco {} no es válida (formato o contenido incompleto), se ignora", archivo);
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo leer la caché en disco {}, se ignora: {}", archivo, e.getMessage());
            return Optional.empty();
        }
    }

    /** Escribe a un temporal en la misma carpeta y lo mueve sobre el destino, para no dejar nunca un JSON a medias. */
    public synchronized void guardar(SnapshotMeta snapshot) {
        if (archivo == null) {
            return;
        }
        Path temporal = null;
        try {
            Path absoluto = archivo.toAbsolutePath();
            Files.createDirectories(absoluto.getParent());
            temporal = Files.createTempFile(absoluto.getParent(), "meta-cache-", ".tmp");
            mapper.writeValue(temporal.toFile(), snapshot);
            try {
                Files.move(temporal, absoluto, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, absoluto, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo guardar la caché en disco {}: {}", archivo, e.getMessage());
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignorada) {
                    // nada más que hacer con un temporal huérfano
                }
            }
        }
    }

    public synchronized void borrar() {
        if (archivo == null) {
            return;
        }
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.warn("No se pudo borrar la caché en disco {}: {}", archivo, e.getMessage());
        }
    }

    private static boolean esValido(SnapshotMeta s) {
        return s != null
                && s.version() == SnapshotMeta.VERSION
                && s.datos() != null
                && s.datos().parche() != null
                && s.datos().cargadoEn() != null
                && !s.datos().heroes().isEmpty()
                && s.matchups() != null;
    }
}
