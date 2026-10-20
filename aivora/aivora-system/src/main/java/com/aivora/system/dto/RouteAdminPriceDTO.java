package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record RouteAdminPriceDTO(@Min(1) @Max(1000) long stars) {}
