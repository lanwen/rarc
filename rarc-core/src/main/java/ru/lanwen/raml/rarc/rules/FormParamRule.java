package ru.lanwen.raml.rarc.rules;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.apache.commons.lang3.StringUtils;
import org.raml.model.parameter.FormParameter;
import ru.lanwen.raml.rarc.api.ra.AddFormParamMethod;

import javax.lang.model.element.Modifier;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static ru.lanwen.raml.rarc.api.ApiResourceClass.sanitize;

/**
 * Created by stassiak
 */
public class FormParamRule implements Rule<FormParameter>{
    RuleFactory ruleFactory;

    public FormParamRule(RuleFactory ruleFactory) {
        this.ruleFactory = ruleFactory;
    }

    @Override
    public void apply(FormParameter param, ResourceClassBuilder resourceClassBuilder) {
        resourceClassBuilder.getApiClass()
                .withMethod(new AddFormParamMethod(param, param.getDisplayName(),
                        ruleFactory.getReq(), resourceClassBuilder.getApiClass()));

        resourceClassBuilder.getDefaultsMethod().forParamDefaults(param.getDisplayName(), param);

        if (param.getEnumeration() != null && !param.getEnumeration().isEmpty()) {
            TypeSpec.Builder enumParam =
                    TypeSpec.enumBuilder(capitalize(sanitize(param.getDisplayName())) + "Param")
                            .addModifiers(Modifier.PUBLIC)
                            .addField(String.class, "value", Modifier.PRIVATE, Modifier.FINAL)
                            .addMethod(MethodSpec.methodBuilder("value")
                                    .addModifiers(Modifier.PUBLIC)
                                    .returns(String.class)
                                    .addStatement("return $N", "value")
                                    .build())
                            .addMethod(MethodSpec.constructorBuilder()
                                    .addParameter(String.class, "value")
                                    .addStatement("this.$N = $N", "value", "value")
                                    .build());
            param.getEnumeration()
                    .forEach(value -> enumParam.addEnumConstant(
                            StringUtils.upperCase(sanitize(value)),
                            TypeSpec.anonymousClassBuilder("$S", value).build()
                    ));

            resourceClassBuilder.getApiClass().withEnum(enumParam.build());

            resourceClassBuilder.getApiClass().withMethod(() -> {
                // для энума
                String sanitized = sanitize(param.getDisplayName());
                return MethodSpec.methodBuilder("with" + capitalize(sanitized))
                        .addJavadoc("required: $L\n", param.isRequired())
                        .addJavadoc("$L\n", isNotEmpty(param.getExample()) ?
                                "example: " + param.getExample() : "")
                        .addJavadoc("@param $L $L\n", sanitized, trimToEmpty(param.getDescription()))
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ClassName.bestGuess(resourceClassBuilder.getApiClass().name()))
                        .varargs(param.isRepeat())
                        .addParameter(param.isRepeat() ?
                                ArrayTypeName.of(ClassName.bestGuess(enumParam.build().name)) :
                                ClassName.bestGuess(enumParam.build().name), sanitized)
                        .addStatement("$L.addQueryParam($S, $L.value())", ruleFactory.getReq().name(),
                                param.getDisplayName(), sanitized)
                        .addStatement("return this", ruleFactory.getReq().name())
                        .build();
            });
        }
    }
}