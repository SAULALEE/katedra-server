# Katedra Server

Este repositorio contiene el backend de **Katedra**, una herramienta de generación de contenido académico impulsada por Inteligencia Artificial para profesores. La aplicación está desarrollada utilizando Spring Boot y una arquitectura de monolito modular.

---

## 🛠️ Requisitos Previos e Instalación

Asegúrate de tener instalado lo siguiente en tu máquina local:

### 1. Java 21 (JDK 21)
El entorno de desarrollo para ejecutar la aplicación. Se recomienda usar [SDKMAN!](https://sdkman.io/) para la gestión de versiones de Java:
```bash
sdk install java 21-open
```

### 2. MySQL 8.0
Motor de base de datos relacional para el almacenamiento persistente. Debe estar en ejecución localmente o en un contenedor accesible.

### 3. Doppler CLI
Cliente para inyectar variables de entorno de forma segura sin usar archivos locales `.env` o contraseñas expuestas en texto plano.

#### 🐧 En Linux (Debian/Ubuntu/macOS)
Puedes utilizar el instalador rápido oficial:
```bash
curl -sLf https://web.doppler.com/install.sh | sh
```
*O vía Homebrew:*
```bash
brew install dopplerhq/cli/doppler
```

#### 🪟 En Windows
Puedes instalarlo mediante gestores de paquetes comunes de Windows:

**Opción A: Winget (Recomendado)**
```powershell
winget install Doppler.DopplerCLI
```

**Opción B: Scoop**
```powershell
scoop bucket add doppler https://github.com/DopplerHQ/scoop-bucket.git
scoop install doppler
```

---

## 🚀 Configuración del Entorno Local

Una vez completadas las instalaciones del sistema, sigue estos pasos:

### 1. Clonar el repositorio
```bash
git clone <url-del-repositorio>
cd katedra-server
```

### 2. Autenticar y enlazar Doppler
Debes conectarse a Doppler y enlazar este directorio local al proyecto de la aplicación:

```bash
# Iniciar sesión en Doppler (solo la primera vez)
doppler login

# Enlazar la carpeta local al proyecto
doppler setup
```
> [!NOTE]
> Cuando ejecutes `doppler setup`, selecciona el proyecto **`katedra-server`** y la configuración de entorno **`dev`** (o tu configuración de desarrollo asignada).

### 3. Ejecutar el servidor de desarrollo
Para iniciar la aplicación local inyectando los secretos de Doppler de forma automática:

```bash
doppler run -- ./mvnw spring-boot:run
```

El servidor de desarrollo correrá por defecto en `http://localhost:8080/api/v1`.

---

## 📦 Comandos y Tareas Disponibles

El proyecto expone las siguientes tareas de Maven a través del wrapper (`mvnw`):

- **`doppler run -- ./mvnw spring-boot:run`**: Levanta el entorno de desarrollo de Spring Boot inyectando las variables de entorno desde Doppler.
- **`./mvnw clean package`**: Compila el proyecto y genera el archivo JAR empaquetado y optimizado en el directorio `target/`.
- **`doppler run -- ./mvnw test`**: Ejecuta la suite de pruebas unitarias e integración con las variables correspondientes.

---

## 🔒 Gestión de Variables de Entorno

* **No crear archivos locales de configuración con credenciales:** El archivo `application.properties` lee directamente del entorno mediante variables como `${DB_URL}` o `${JWT_SECRET}`. Doppler se encarga de inyectar las variables directamente en el proceso de ejecución.
* **Variables esenciales para desarrollo:**
  - `DB_URL`: URL de conexión a la base de datos MySQL (ej. `jdbc:mysql://localhost:3306/katedra_db`).
  - `DB_USER`: Nombre de usuario de la base de datos.
  - `DB_PASSWORD`: Contraseña del usuario de la base de datos.
  - `JWT_SECRET`: Clave secreta para la firma y verificación de JSON Web Tokens (JWT).
  - `JWT_EXPIRATION`: Tiempo de vida de los tokens en milisegundos.
* **Agregar nuevos secretos:** Si necesitas registrar una nueva variable (por ejemplo, `OPENAI_API_KEY`), agrégala en la plataforma de Doppler para el proyecto `katedra-server` en el entorno respectivo y notifica al equipo para sincronizar los cambios.