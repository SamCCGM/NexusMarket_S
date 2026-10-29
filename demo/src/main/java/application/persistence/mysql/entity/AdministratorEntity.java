package application.persistence.mysql.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "administrators")
@PrimaryKeyJoinColumn(name = "id")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AdministratorEntity extends UserEntity {

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions; // JSON string for granular permissions

    @Column(name = "is_super_admin")
    private boolean superAdmin = false;
}