package com.Let.s_Code.nexus;

import com.Let.s_Code.nexus.Entity.Role;
import com.Let.s_Code.nexus.Repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NexusApplication {

	public static void main(String[] args) {
		SpringApplication.run(NexusApplication.class, args);
	}
    // Inside your main NexusApplication class, add this:
    @Bean
    CommandLineRunner init(RoleRepository roleRepository) {
        return args -> {
            if (roleRepository.findByName("ADMIN").isEmpty()) {
                roleRepository.save(new Role(null, "ADMIN", 1));
                roleRepository.save(new Role(null, "USER", 2));
            }
        };
    }
}
