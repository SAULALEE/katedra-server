package Katedra.Server.student.service;

import Katedra.Server.model.RolUsuario;
import Katedra.Server.model.Usuario;
import Katedra.Server.repository.UsuarioRepository;
import Katedra.Server.student.dto.StudentProfileResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentService {

    private final UsuarioRepository usuarioRepository;

    public StudentService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public StudentProfileResponseDTO getMyProfile(String email) {
        Usuario student = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado"));

        if (student.getRol() != RolUsuario.ROLE_ALUMNO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso exclusivo para alumnos");
        }

        return new StudentProfileResponseDTO(
                student.getId(),
                student.getEmail(),
                student.getNombre(),
                student.getCreatedAt()
        );
    }
}
