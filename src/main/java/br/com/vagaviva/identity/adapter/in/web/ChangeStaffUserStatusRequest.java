package br.com.vagaviva.identity.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChangeStaffUserStatusRequest(@Schema(example = "false") @NotNull Boolean active) {
}
