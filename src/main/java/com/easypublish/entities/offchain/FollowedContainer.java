package com.easypublish.entities.offchain;

import jakarta.persistence.*;

@Entity
@Table(name = "offchain_followed_containers",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"userAddress", "containerId"}
        )
)
public class FollowedContainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String userAddress;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String containerId;

    public FollowedContainer() {}

    public FollowedContainer(String userAddress, String containerId) {
        this.userAddress = userAddress;
        this.containerId = containerId;
    }

    public Long getId() {
        return id;
    }

    public String getUserAddress() {
        return userAddress;
    }

    public void setUserAddress(String userAddress) {
        this.userAddress = userAddress;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }
}
