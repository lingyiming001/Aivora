package com.aivora.character.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record EngagementChangeDTO(Boolean favorite, @Min(-1) @Max(1) Integer vote) {}
