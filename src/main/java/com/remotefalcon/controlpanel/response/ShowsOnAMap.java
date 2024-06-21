package com.remotefalcon.controlpanel.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShowsOnAMap {
    private String showName;
    private Float showLatitude;
    private Float showLongitude;
}
