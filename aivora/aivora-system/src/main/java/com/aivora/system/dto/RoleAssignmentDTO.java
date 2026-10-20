package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record RoleAssignmentDTO(@NotNull @Size(max = 20) List<@Positive Long> roleIds) {}
