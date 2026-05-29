# Practica 7

## Antes de empezar: 
1. Crear repositorio en github
2. Vincular al proyecto: 
   ```bash
   git init
   git add .
   git commit -m "Initial files"
   git remote add origin https://github.com/candeladavila/IPS-P7-GitHubActions-Docker.git 
   git push
    ```
3. Comprobamos que el proyecto compila
   ```bash
    chmod +x ./mvnw
    ./mvnw clean package -DskipTests
    ```

## DockerFile
1. En la raíz del proyecto crear fichero Dockerfile
2. Contenido Dockerfile
   ```Dockerfile
    FROM eclipse-temurin:17-jdk
    
    WORKDIR /app
    
    COPY target/Practica6-0.0.1-SNAPSHOT.jar app.jar
    
    EXPOSE 8080
    
    ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

Este fichero Dockerfile lo que hace es exponer la aplicación java (.jar generado al compilar) y exponerlo en el puerto 8080.

### Opcional: Crear fichero .dockerignore
Crear fichero .dockerignore en la raiz del proyecto con este contenido: 
```dockerignore
.git
.github
target
!target/Practica6-0.0.1-SNAPSHOT.jar
*.md
```

## Construir imagen de Docker en local
1. Empaquetar el proyecto
    ```bash
    ./mvnw clean package -DskipTests
    ```
2. Construir imagen
   ```bash
   docker build -t practica7-hospital:local .
    ```
3. Comprobar que se ha creado bien (ver que aparezca la imagen practica7-hospital:local)
   ```bash
   docker images
   ```
4. Probar imagen en local 
   ```bash
   docker run --rm -p 8080:8080 practica7-hospital:local
   ```
   Para probarlo (primero creamos el médico): 
   ```bash
   curl -X POST http://localhost:8080/medico \
   -H "Content-Type: application/json" \
   -d '{
   "dni": "835",
   "nombre": "Miguel",
   "especialidad": "Ginecologia"
   }'
   ```

## Subir imagen a DockerHub 
### La primera vez
1. Crear repositorio en DockerHub (iniciar sesión en github)
2. Login desde el terminal
3. Etiquetar la imagen en docker
   ```bash
   docker tag nombreimagenlocal:etiqueta USUARIO_DOCKER/nombreRepoDocker:latest
   ```
4. Subir la imagen a docker
   ```bash
   docker push USUARIO_DOCKER/nombreRepoDocker:latest
   ```
   
## Ficheros de kubernetes
1. Crear una carpeta: k8s/
2. Crear los ficheros: 
   - deployment.yml
     ```yml
      apiVersion: apps/v1
      kind: Deployment
      metadata:
         name: hospital-app
      spec:
         replicas: 1
         selector:
            matchLabels:
               app: hospital-app
         template:
            metadata:
               labels:
                  app: hospital-app
            spec:
               containers:
                  - name: hospital-app
                    image: TU_USUARIO_DOCKER/practica7-hospital:latest
                    imagePullPolicy: Always
                    ports:
                     - containerPort: 8080
      ```
   - service.yml
      ```yml
      apiVersion: v1
      kind: Service
      metadata:
         name: hospital-service
      spec:
         type: NodePort
         selector:
            app: hospital-app
         ports:
          - port: 8080
            targetPort: 8080
            nodePort: 30080
      ```
3. Desplegar kubernetes
   ```bash
   kubectl apply -f k8s/deployment.yml
   kubectl apply -f k8s/service.yml
   ```
4. Comprobar que se ha desplegado bien
   ```bash
   kubectl get deployments
   kubectl get pods
   kubectl get services
   ```

## Conectar GitHub Actions con DockerHub
1. Crear usuario/token de DockerHub
   1. Account Settings > Settings > Personal Access Token
   2. Generate new token
   3. Access Permissions (Read, Write, Delete)
   4. Generate
   5. Copiar la contraseña (MUY IMPORTANTE)
2. En el repositorio de github: 
   1. Settings > Secrets and Variables > Actions > New Repository Secret
   2. Crear secreto: DOCKERHUB_USERNAME (usuario de dockerhub)
   3. Crear secreto: DOCKERHUB_TOKEN (poner el valor que hemos copiado de dockerHub)

## Configurar GitHub Runner
1. En el repositorio de GitHub: 
   1. Settings > Actions > Runner 
   2. New self-hosted runner
   3. Copiar los comandos y ejecutarlos en el terminal de nuestro proyecto
2. Actualizar el runner para usarlo como servicio
   1. En la carpeta actions-runner ejecutar
      ```bash
      ./svc.sh install
      ./svc.sh start
      ```
   2. Ahora debería aparecer en el repositorio de github en: Settings > Actions > Runners el runner que hemos creado

## Creamos el workflow 
1. Creamos el directorio .github/workflows en el directorio principal del proyecto
2. Creamos el fichero docker-publish.yml
   ```yml
   name: Docker Publish and Deploy
   
   on:
     push:
       branches: [ main, master ]
   
   jobs:
     build-push-deploy:
       runs-on: self-hosted
   
       steps:
         - name: Descargar codigo
           uses: actions/checkout@v4
   
         - name: Configurar Java
           uses: actions/setup-java@v4
           with:
             distribution: temurin
             java-version: '17'
             cache: maven
   
         - name: Dar permisos a Maven Wrapper
           run: chmod +x ./mvnw
   
         - name: Compilar aplicacion
           run: ./mvnw clean package -DskipTests --no-transfer-progress
   
         - name: Login en Docker Hub
           uses: docker/login-action@v4
           with:
             username: ${{ secrets.DOCKERHUB_USERNAME }}
             password: ${{ secrets.DOCKERHUB_TOKEN }}
   
         - name: Construir imagen Docker
           run: docker build -t ${{ secrets.DOCKERHUB_USERNAME }}/practica7-hospital:${{ github.sha }} .
   
         - name: Etiquetar imagen como latest
           run: docker tag ${{ secrets.DOCKERHUB_USERNAME }}/practica7-hospital:${{ github.sha }} ${{ secrets.DOCKERHUB_USERNAME }}/practica7-hospital:latest
   
         - name: Subir imagen con tag del commit
           run: docker push ${{ secrets.DOCKERHUB_USERNAME }}/practica7-hospital:${{ github.sha }}
   
         - name: Subir imagen latest
           run: docker push ${{ secrets.DOCKERHUB_USERNAME }}/practica7-hospital:latest
   
         - name: Desplegar en Kubernetes
           run: |
             kubectl apply -f k8s/deployment.yml
             kubectl apply -f k8s/service.yml
             kubectl set image deployment/hospital-app hospital-app=${{ secrets.DOCKERHUB_USERNAME }}/practica7-hospital:${{ github.sha }}
             kubectl rollout status deployment/hospital-app
   ```
   Este workflow lo que hace es: 
   1. Se ejecuta con push a main/master.
   2. Usa tu runner local.
   3. Compila el proyecto.
   4. Hace login en Docker Hub.
   5. Construye la imagen Docker.
   6. La sube a Docker Hub.
   7. Despliega o actualiza Kubernetes.

## Probamos el workflow (commit y push)
```bash 
echo "actions-runner/" >> .gitignore     
git add .gitignore  
git commit -m "Remove local actions runner from repository"
git add .
git commit -m "Add Docker and Kubernetes deployment workflow"
git push
```