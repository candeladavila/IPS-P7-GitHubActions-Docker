package com.uma.example.springuma.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uma.example.springuma.integration.base.AbstractIntegration;
import com.uma.example.springuma.model.Medico;

public class MedicoControllerMockMvcIT extends AbstractIntegration {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Medico medico;

    @BeforeEach
    void setUp() {
        medico = new Medico();
        medico.setId(1L);
        medico.setDni("835");
        medico.setNombre("Miguel");
        medico.setEspecialidad("Ginecologia");
    }

    private void crearMedico(Medico medico) throws Exception {
        this.mockMvc.perform(post("/medico")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(medico)))
                .andExpect(status().isCreated());
    }


    /// --------------------- TESTS -----------------------------
    @Test
    @DisplayName("Crear médico y recuperarlo por id")
    void saveMedicoAndRecuperarById() throws Exception {
        //Crear
        crearMedico(medico);

        //Recuperarlo
        mockMvc.perform(get("/medico/{id}", medico.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.dni").value("835"))
                .andExpect(jsonPath("$.nombre").value("Miguel"))
                .andExpect(jsonPath("$.especialidad").value("Ginecologia"));
    }

    @Test
    @DisplayName("Buscar medico por DNI")
    void getMedicoByDni_DevuelveMedico() throws Exception {
        crearMedico(medico);

        mockMvc.perform(get("/medico/dni/{dni}", medico.getDni()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value("835"))
                .andExpect(jsonPath("$.nombre").value("Miguel"))
                .andExpect(jsonPath("$.especialidad").value("Ginecologia"));
    }

    @Test
    @DisplayName("Actualizar medico")
    void updateMedico_ModificaDatos() throws Exception {
        crearMedico(medico);

        medico.setNombre("Miguel Actualizado");
        medico.setEspecialidad("Radiologia");

        mockMvc.perform(put("/medico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(medico)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/medico/{id}", medico.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value("835"))
                .andExpect(jsonPath("$.nombre").value("Miguel Actualizado"))
                .andExpect(jsonPath("$.especialidad").value("Radiologia"));
    }

    @Test
    @DisplayName("Eliminar medico")
    void deleteMedico_EliminaMedicoExistente() throws Exception {
        crearMedico(medico);

        mockMvc.perform(delete("/medico/{id}", medico.getId()))
                .andExpect(status().isOk());
    }
}
