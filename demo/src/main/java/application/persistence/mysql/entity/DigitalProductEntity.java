package application.persistence.mysql.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "digital_products")
@PrimaryKeyJoinColumn(name = "id")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DigitalProductEntity extends ProductEntity {

    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    @Column(name = "file_size_mb")
    private Long fileSizeMb;

    @Column(name = "file_format", length = 50)
    private String fileFormat;

    @Column(name = "license_key_required")
    private boolean licenseKeyRequired = false;

    @Column(name = "max_downloads")
    private Integer maxDownloads = -1; // -1 = unlimited
}