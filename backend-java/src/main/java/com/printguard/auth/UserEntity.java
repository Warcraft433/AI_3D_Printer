package com.printguard.auth;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "auth_user")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    @Column(name = "is_superuser", nullable = false)
    private Boolean isSuperuser = false;

    @Column(nullable = false, unique = true, length = 150)
    private String username;

    @Column(name = "first_name", nullable = false, length = 150)
    private String firstName = "";

    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName = "";

    @Column(nullable = false, length = 254)
    private String email = "";

    @Column(name = "is_staff", nullable = false)
    private Boolean isStaff = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "date_joined", nullable = false)
    private OffsetDateTime dateJoined = OffsetDateTime.now();

    public UserEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public OffsetDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(OffsetDateTime lastLogin) { this.lastLogin = lastLogin; }

    public Boolean getIsSuperuser() { return isSuperuser; }
    public void setIsSuperuser(Boolean isSuperuser) { this.isSuperuser = isSuperuser; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Boolean getIsStaff() { return isStaff; }
    public void setIsStaff(Boolean isStaff) { this.isStaff = isStaff; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getDateJoined() { return dateJoined; }
    public void setDateJoined(OffsetDateTime dateJoined) { this.dateJoined = dateJoined; }
}
