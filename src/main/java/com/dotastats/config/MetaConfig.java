package com.dotastats.config;

import com.dotastats.analizadores.habilidad.HabilidadProperties;
import com.dotastats.analizadores.habilidad.JuegoProperties;
import com.dotastats.analizadores.habilidad.RotacionProperties;
import com.dotastats.draft.SugerenciasProperties;
import com.dotastats.meta.MetaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MetaProperties.class, SugerenciasProperties.class, HabilidadProperties.class,
        RotacionProperties.class, JuegoProperties.class})
public class MetaConfig {
}
