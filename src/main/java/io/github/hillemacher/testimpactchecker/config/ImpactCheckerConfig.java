package io.github.hillemacher.testimpactchecker.config;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImpactCheckerConfig {

	private List<String> annotations;

	private String baseRef;

	private String targetRef;

}
