package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record RouteAdminTierDTO(@Pattern(regexp = "STANDARD|VIP|SVIP") @NotNull String tier) {}
