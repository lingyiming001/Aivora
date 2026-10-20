package com.aivora.chat.dto;

import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public record ChatMediaAudioDTO(@NotBlank @Size(max = 6000000) String audio) {}
