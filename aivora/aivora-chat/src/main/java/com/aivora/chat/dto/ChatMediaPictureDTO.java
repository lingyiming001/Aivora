package com.aivora.chat.dto;

import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public record ChatMediaPictureDTO(@NotBlank @Size(max = 800) String prompt) {}
