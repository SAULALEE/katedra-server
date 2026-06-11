# 1. Clonar el repositorio
git clone https://github.com/tu-usuario/katedra-server.git
cd katedra-server

# 2. Configurar Doppler (Inicia sesión y selecciona proyecto/enviroment)
doppler login
doppler setup

# 3. Instalar dependencias de Maven
mvn clean install

# 4. Levantar la base de datos (asegúrate de tener Docker corriendo)
docker-compose up -d

# 5. Ejecutar la aplicación inyectando secretos de Doppler
doppler run -- mvn spring-boot:run
