package com.aivora.chat.dto;

import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public record ChatMediaImageDTO(@NotBlank @Size(max = 300000) String image) {}
