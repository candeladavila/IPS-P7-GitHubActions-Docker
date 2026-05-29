package com.uma.example.springuma.integration;

import static org.hamcrest.Matchers.containsString;

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
import com.uma.example.springuma.model.Medico;
import com.uma.example.springuma.model.Paciente;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ImagenControllerWebTestClientIT extends AbstractIntegration {

    @LocalServerPort
    private Integer port;

    private WebTestClient testClient;

    private Paciente paciente;
    private Medico medico;

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
    }

    private void subirImagen(String nombreArchivo) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        builder.part("image",
                new FileSystemResource(Paths.get("src/test/resources/" + nombreArchivo).toFile()));

        builder.part("paciente", paciente);

        testClient.post().uri("/imagen")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(containsString("file uploaded successfully"));
    }

    private Imagen subirYRecuperarPrimeraImagen() {
        subirImagen("healthy.png");

        List<Imagen> imagenes = testClient.get()
                .uri("/imagen/paciente/{id}", paciente.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<Imagen>>() {})
                .returnResult()
                .getResponseBody();

        return imagenes.get(0);
    }

    @Test
    @DisplayName("Subir imagen y listarla por paciente")
    void uploadImage_ListaImagenesDelPaciente() {
        subirImagen("healthy.png");

        testClient.get().uri("/imagen/paciente/{id}", paciente.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].nombre").isEqualTo("healthy.png")
                .jsonPath("$[0].paciente.dni").isEqualTo("888");
    }

    @Test
    @DisplayName("Descargar informacion y bytes de imagen")
    void getImagenInfoYDownload_DevuelveImagen() {
        Imagen imagen = subirYRecuperarPrimeraImagen();

        testClient.get().uri("/imagen/info/{id}", imagen.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo((int) imagen.getId())
                .jsonPath("$.nombre").isEqualTo("healthy.png");

        testClient.get().uri("/imagen/{id}", imagen.getId())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.IMAGE_PNG)
                .expectBody(byte[].class);
    }

    @Test
    @DisplayName("Realizar prediccion de una imagen")
    void predictImagen_DevuelveCancerONotCancer() {
        Imagen imagen = subirYRecuperarPrimeraImagen();

        testClient.get().uri("/imagen/predict/{id}", imagen.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(containsString("status"))
                .value(containsString("score"))
                .value(resultado -> org.junit.jupiter.api.Assertions.assertTrue(
                        resultado.contains("Cancer") || resultado.contains("Not cancer")));
    }

    @Test
    @DisplayName("Eliminar imagen")
    void deleteImagen_EliminaImagenExistente() {
        Imagen imagen = subirYRecuperarPrimeraImagen();

        testClient.delete().uri("/imagen/{id}", imagen.getId())
                .exchange()
                .expectStatus().isNoContent();
    }
}