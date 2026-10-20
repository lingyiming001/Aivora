package com.aivora.payment.dto;

import jakarta.validation.constraints.Positive;
import java.util.*;

public record RechargePlaceDTO(@Positive long packageId) {}
