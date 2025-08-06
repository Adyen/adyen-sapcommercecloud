package com.adyen.backoffice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.adyen.backoffice.dto.MerchantDataWsDTO;
import com.adyen.backoffice.dto.MerchantResponseWsDTO;

import com.adyen.backoffice.service.AdyenManagementService;


@Controller
@RequestMapping("/api/merchants")
public class MerchantController {

	@Autowired
	private AdyenManagementService adyenManagementService;

	@GetMapping
	public ResponseEntity<MerchantResponseWsDTO> getMerchants(
			@RequestParam(defaultValue = "10") Integer pageSize,
			@RequestParam(defaultValue = "1") Integer pageNumber) {
		
		MerchantResponseWsDTO response = adyenManagementService.getMerchants(pageSize, pageNumber);
		
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{merchantId}")
	public ResponseEntity<MerchantDataWsDTO> getMerchantById(@PathVariable String merchantId) {
		
		MerchantDataWsDTO merchant = adyenManagementService.getMerchantById(merchantId);
		
		return ResponseEntity.ok(merchant);
	}
}