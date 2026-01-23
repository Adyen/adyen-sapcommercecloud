package com.adyen.service.model;

public class DPAResult<M, R> {

	private final boolean hasResult;
	private final boolean success;
	private final String resultCode;
	private final String resultDesc;
	private final M resultModel;
	private final R rawResultList;

	protected DPAResult(
			boolean hasResult,
			boolean success,
			String resultCode,
			String resultDesc,
			M resultModel,
			R rawResultList
	) {
		this.hasResult = hasResult;
		this.success = success;
		this.resultCode = resultCode;
		this.resultDesc = resultDesc;
		this.resultModel = resultModel;
		this.rawResultList = rawResultList;
	}

	public boolean hasResult() {
		return hasResult;
	}

	public boolean isSuccess() {
		return success;
	}

	public String getResultCode() {
		return resultCode;
	}

	public String getResultDesc() {
		return resultDesc;
	}

	public M getResultModel() {
		return resultModel;
	}

	public R getRawResultList() {
		return rawResultList;
	}
}
