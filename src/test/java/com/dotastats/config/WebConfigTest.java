package com.dotastats.config;

import com.dotastats.draft.CatalogoHeroesService;
import com.dotastats.draft.DraftController;
import com.dotastats.draft.HeroeAliasResolver;
import com.dotastats.draft.MotorSugerencias;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DraftController.class)
@Import(WebConfig.class)
@TestPropertySource(properties = "dotastats.web.cors-origins=http://localhost:5173,http://otro.local:3000")
class WebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HeroeAliasResolver resolver;
    @MockBean
    private MotorSugerencias motorSugerencias;
    @MockBean
    private CatalogoHeroesService catalogo;

    @Test
    void permiteElOrigenConfigurado() throws Exception {
        mockMvc.perform(get("/api/draft/roles").header("Origin", "http://otro.local:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://otro.local:3000"));
    }

    @Test
    void respondeElPreflightDeUnPostConJson() throws Exception {
        mockMvc.perform(options("/api/draft/suggest")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rechazaUnOrigenNoConfigurado() throws Exception {
        mockMvc.perform(get("/api/draft/roles").header("Origin", "http://malo.example"))
                .andExpect(status().isForbidden());
    }
}
