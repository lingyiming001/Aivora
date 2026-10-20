package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record AccessStatusDTO(@Pattern(regexp = "ACTIVE|DISABLED") @NotNull String status) {}
