package com.finpulse.repository;

import com.finpulse.entity.Company;
import com.finpulse.entity.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company,Long> {

    Optional<Company> findByApiHashCode(String apiHashCode);

   Optional<Company>  findByCompanyCode(String companyCode);

    List<Company> findByCompanyStatus(CompanyStatus status);

}
