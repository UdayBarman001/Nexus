package com.Let.s_Code.nexus.Entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Generates all Getters, Setters, toString, equals, and hashCode automatically
@NoArgsConstructor // Generates the empty constructor Hibernate needs
@AllArgsConstructor // Generates a constructor with all fields
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(name = "access_level")
    private Integer accessLevel;
}
