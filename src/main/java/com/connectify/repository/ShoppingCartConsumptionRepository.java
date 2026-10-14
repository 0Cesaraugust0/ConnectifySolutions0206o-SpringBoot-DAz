package com.connectify.repository;

import com.connectify.entity.ShoppingCartConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingCartConsumptionRepository extends JpaRepository<ShoppingCartConsumption, String> {
}
