package com.aivora.chat.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ChatNewSessionDTO(@Positive long characterId) {}
