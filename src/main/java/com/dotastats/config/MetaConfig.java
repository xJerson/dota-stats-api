package com.dotastats.config;

import com.dotastats.analizadores.habilidad.HabilidadProperties;
import com.dotastats.draft.SugerenciasProperties;
import com.dotastats.meta.MetaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MetaProperties.class, SugerenciasProperties.class, HabilidadProperties.class})
public class MetaConfig {
}
