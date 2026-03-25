package com.easypublish.entities;

import jakarta.persistence.*;
import java.math.BigInteger;

@Entity
@Table(name = "user_data")
public class UserDataEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_user;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String address;

    private BigInteger first_seen_at_ms;
    private BigInteger last_seen_at_ms;

    // getters / setters

    public Long getId_user() {
        return id_user;
    }

    public void setId_user(Long id_user) {
        this.id_user = id_user;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigInteger getFirst_seen_at_ms() {
        return first_seen_at_ms;
    }

    public void setFirst_seen_at_ms(BigInteger first_seen_at_ms) {
        this.first_seen_at_ms = first_seen_at_ms;
    }

    public BigInteger getLast_seen_at_ms() {
        return last_seen_at_ms;
    }

    public void setLast_seen_at_ms(BigInteger last_seen_at_ms) {
        this.last_seen_at_ms = last_seen_at_ms;
    }
}
