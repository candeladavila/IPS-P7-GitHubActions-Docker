package com.uma.example.springuma.integration;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.uma.example.springuma.integration.base.AbstractIntegration;
import com.uma.example.springuma.model.Imagen;
import com.uma.example.springuma.model.Informe;
import com.uma.example.springuma.model.Medico;
import com.uma.example.springuma.model.Paciente;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class InformeControllerWebTestClientIT extends AbstractIntegration {

    @LocalServerPort
    private Integer port;

    private WebTestClient testClient;

    private Medico medico;
    private Paciente paciente;
    private Imagen imagen;
    private Informe informe;

    @PostConstruct
    public void init() {
        testClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofMillis(300000))
                .build();
    }

    @BeforeEach
    void setUp() {
        medico = new Medico();
        medico.setId(1L);
        medico.setDni("835");
        medico.setNombre("Miguel");
        medico.setEspecialidad("Ginecologo");

        paciente = new Paciente();
        paciente.setId(1L);
        paciente.setDni("888");
        paciente.setNombre("Maria");
        paciente.setEdad(20);
        paciente.setCita("Ginecologia");
        paciente.setMedico(medico);

        testClient.post().uri("/medico")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(medico), Medico.class)
                .exchange()
                .expectStatus().isCreated();

        testClient.post().uri("/paciente")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(paciente), Paciente.class)
                .exchange()
                .expectStatus().isCreated();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        builder.part("image",
                new FileSystemResource(Paths.get("src/test/resources/healthy.png").toFile()));

        builder.part("paciente", paciente);

        testClient.post().uri("/imagen")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk();

        List<Imagen> imagenes = testClient.get()
                .uri("/imagen/paciente/{id}", paciente.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<Imagen>>() {})
                .returnResult()
                .getResponseBody();

        imagen = imagenes.get(0);

        informe = new Informe();
        informe.setContenido("Informe generado en prueba de integracion");
        informe.setImagen(imagen);
    }

    private Informe crearYRecuperarPrimerInforme() {
        testClient.post().uri("/informe")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(informe), Informe.class)
                .exchange()
                .expectStatus().isCreated();

        List<Informe> informes = testClient.get()
                .uri("/informe/imagen/{id}", imagen.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<Informe>>() {})
                .returnResult()
                .getResponseBody();

        return informes.get(0);
    }

    @Test
    @DisplayName("Crear informe y listarlo por imagen")
    void saveInforme_ListaInformesPorImagen() {
        crearYRecuperarPrimerInforme();

        testClient.get().uri("/informe/imagen/{id}", imagen.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].contenido").isEqualTo("Informe generado en prueba de integracion")
                .jsonPath("$[0].prediccion").exists()
                .jsonPath("$[0].imagen.id").isEqualTo((int) imagen.getId());
    }

    @Test
    @DisplayName("Recuperar informe por id")
    void getInforme_DevuelveInformeCreado() {
        Informe informeCreado = crearYRecuperarPrimerInforme();

        testClient.get().uri("/informe/{id}", informeCreado.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo((int) informeCreado.getId())
                .jsonPath("$.contenido").isEqualTo("Informe generado en prueba de integracion")
                .jsonPath("$.prediccion").exists();
    }

    @Test
    @DisplayName("Eliminar informe")
    void deleteInforme_EliminaInformeExistente() {
        Informe informeCreado = crearYRecuperarPrimerInforme();

        testClient.delete().uri("/informe/{id}", informeCreado.getId())
                .exchange()
                .expectStatus().isNoContent();
    }
}