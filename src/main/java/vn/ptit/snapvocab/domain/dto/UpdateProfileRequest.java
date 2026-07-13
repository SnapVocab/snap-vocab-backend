package vn.ptit.snapvocab.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    @Size(max = 200)
    private String bio;
}
