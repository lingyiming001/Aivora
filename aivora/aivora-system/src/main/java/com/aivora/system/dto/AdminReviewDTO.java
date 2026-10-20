package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record AdminReviewDTO(@Size(max = 1000) String reason) {}
