package com.easypublish.entities.publish;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "parsed_publish_target")
public class PublishTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String domain;

    @Column(name = "base_url", columnDefinition = "TEXT")
    private String baseUrl;

    private boolean enabled;

    @ElementCollection
    @CollectionTable(name = "parsed_publish_target_paths", joinColumns = @JoinColumn(name = "publish_target_id"))
    @Column(name = "path", columnDefinition = "TEXT")
    private List<String> paths;

    // 🔹 New fields for linking to objects
    @Column(name = "data_item_id", columnDefinition = "TEXT")
    private String dataItemId;

    @Column(name = "data_type_id", columnDefinition = "TEXT")
    private String dataTypeId;

    @Column(name = "container_id", columnDefinition = "TEXT")
    private String containerId;

    @Column(name = "data_item_verification_id", columnDefinition = "TEXT")
    private String dataItemVerificationId;

    // Constructors
    public PublishTarget() {}

    public PublishTarget(
            String domain,
            String baseUrl,
            boolean enabled,
            List<String> paths,
            String dataItemId,
            String dataTypeId,
            String containerId,
            String dataItemVerificationId
    ) {
        this.domain = domain;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.paths = paths;
        this.dataItemId = dataItemId;
        this.dataTypeId = dataTypeId;
        this.containerId = containerId;
        this.dataItemVerificationId = dataItemVerificationId;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getPaths() { return paths; }
    public void setPaths(List<String> paths) { this.paths = paths; }

    public String getDataItemId() { return dataItemId; }
    public void setDataItemId(String dataItemId) { this.dataItemId = dataItemId; }

    public String getDataTypeId() { return dataTypeId; }
    public void setDataTypeId(String dataTypeId) { this.dataTypeId = dataTypeId; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public String getDataItemVerificationId() { return dataItemVerificationId; }
    public void setDataItemVerificationId(String dataItemVerificationId) { this.dataItemVerificationId = dataItemVerificationId; }
}
