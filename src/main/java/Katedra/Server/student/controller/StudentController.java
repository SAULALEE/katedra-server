package Katedra.Server.student.controller;

import Katedra.Server.student.dto.StudentProfileResponseDTO;
import Katedra.Server.student.service.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/me")
    public ResponseEntity<StudentProfileResponseDTO> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(studentService.getMyProfile(authentication.getName()));
    }
}
