package com.mangofactory.swagger.readers

import com.fasterxml.classmate.TypeResolver
import com.mangofactory.swagger.configuration.SpringSwaggerConfig
import com.mangofactory.swagger.configuration.SwaggerGlobalSettings
import com.mangofactory.swagger.dummy.DummyModels
import com.mangofactory.swagger.dummy.controllers.BusinessService
import com.mangofactory.swagger.dummy.controllers.PetService
import com.mangofactory.swagger.mixins.ApiOperationSupport
import com.mangofactory.swagger.mixins.JsonSupport
import com.mangofactory.swagger.mixins.ModelProviderSupport
import com.mangofactory.swagger.mixins.RequestMappingSupport
import com.mangofactory.swagger.models.alternates.WildcardType
import com.mangofactory.swagger.scanners.RequestMappingContext
import com.wordnik.swagger.model.ApiDescription
import com.wordnik.swagger.model.Model
import com.wordnik.swagger.model.ModelProperty
import com.wordnik.swagger.model.Operation
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.method.HandlerMethod
import spock.lang.Specification

import javax.servlet.http.HttpServletResponse

import static com.mangofactory.swagger.ScalaUtils.*
import static com.mangofactory.swagger.models.alternates.Alternates.*

@Mixin([RequestMappingSupport, ApiOperationSupport, ModelProviderSupport, JsonSupport])
class ApiModelReaderSpec extends Specification {

  def "Method return type model"() {
    given:
      RequestMappingContext context = contextWithApiDescription(dummyHandlerMethod('methodWithConcreteResponseBody'))
    when:
      ApiModelReader apiModelReader = new ApiModelReader(defaultModelProvider())
      apiModelReader.execute(context)
      Map<String, Object> result = context.getResult()

    then:
      Map<String, Model> models = result.get("models")
      Model model = models['BusinessModel']
      model.id == 'BusinessModel'
      model.name() == 'BusinessModel'
      model.qualifiedType() == 'com.mangofactory.swagger.dummy.DummyModels$BusinessModel'

      Map<String, ModelProperty> modelProperties = fromScalaMap(model.properties())
      modelProperties.size() == 2

      ModelProperty nameProp = modelProperties['name']
      nameProp.type() == 'string'
      nameProp.qualifiedType() == 'java.lang.String'
      nameProp.position() == 0
      nameProp.required() == false
      nameProp.description() == toOption(null)
//      "${nameProp.allowableValues().getClass()}".contains('com.wordnik.swagger.model.AnyAllowableValues')
      fromOption(nameProp.items()) == null



      //TODO test these remaining
//      println model.description()
//      println model.baseModel()
//      println model.discriminator()
//      println model.subTypes()
  }

  def "Annotated model"() {
    given:
      RequestMappingContext context = contextWithApiDescription(dummyHandlerMethod('methodWithModelAnnotations'))
    when:
      ApiModelReader apiModelReader = new ApiModelReader(defaultModelProvider())
      apiModelReader.execute(context)
      Map<String, Object> result = context.getResult()

    then:
      Map<String, Model> models = result.get("models")
      Model model = models['AnnotatedBusinessModel']
      model.id == 'AnnotatedBusinessModel'
      model.name() == 'AnnotatedBusinessModel'
      model.qualifiedType() == 'com.mangofactory.swagger.dummy.DummyModels$AnnotatedBusinessModel'

      Map<String, ModelProperty> modelProps = fromScalaMap(model.properties())
      ModelProperty prop = modelProps.name
      prop.type == 'string'
      fromOption(prop.description()) == 'The name of this business'
      prop.required() == true
      println swaggerCoreSerialize(model)

      fromOption(modelProps.numEmployees.description()) == 'Total number of current employees'
      modelProps.numEmployees.required() == false



  }

  def "Should pull models from Api Operation response class"() {
    given:

      RequestMappingContext context = contextWithApiDescription(dummyHandlerMethod('methodApiResponseClass'), null)

    when:
      ApiModelReader apiModelReader = new ApiModelReader(defaultModelProvider())
      apiModelReader.execute(context)
      Map<String, Object> result = context.getResult()

      Map<String, Model> models = result.get("models")
    then:
      println models
      models['FunkyBusiness'].qualifiedType() == 'com.mangofactory.swagger.dummy.DummyModels$FunkyBusiness'
  }

  def contextWithApiDescription(HandlerMethod handlerMethod, List<Operation> operationList = null) {
    RequestMappingContext context = new RequestMappingContext(requestMappingInfo('/somePath'), handlerMethod)
    def scalaOpList = null == operationList ? emptyScalaList() : toScalaList(operationList)
    ApiDescription description = new ApiDescription(
          "anyPath",
          toOption("anyDescription"),
          scalaOpList
    )
    context.put("apiDescriptionList", [description])

    def settings = new SwaggerGlobalSettings()
    settings.ignorableParameterTypes = new SpringSwaggerConfig().defaultIgnorableParameterTypes();
    settings.alternateTypeProvider = new SpringSwaggerConfig().defaultAlternateTypeProvider();
    context.put("swaggerGlobalSettings", settings)
    context
  }

  def "should only generate models for request parameters that are annotated with Springs RequestBody"() {
    given:
      HandlerMethod handlerMethod = dummyHandlerMethod('methodParameterWithRequestBodyAnnotation',
            DummyModels.BusinessModel,
            HttpServletResponse.class,
            DummyModels.AnnotatedBusinessModel.class
      )
      RequestMappingContext context = new RequestMappingContext(requestMappingInfo('/somePath'), handlerMethod)

      def settings = new SwaggerGlobalSettings()

      def config = new SpringSwaggerConfig()
      settings.ignorableParameterTypes = config.defaultIgnorableParameterTypes()
      settings.alternateTypeProvider = config.defaultAlternateTypeProvider()
      context.put("swaggerGlobalSettings", settings)
    when:
      ApiModelReader apiModelReader = new ApiModelReader(defaultModelProvider())
      apiModelReader.execute(context)
      Map<String, Object> result = context.getResult()
    then:
      Map<String, Model> models = result.get("models")
      models.size() == 2 // instead of 3
      models.containsKey("BusinessModel")
      models.containsKey("Void")

  }

  def "Generates the correct models when there is a Map object in the input parameter"() {
    given:
      HandlerMethod handlerMethod = handlerMethodIn(PetService, 'echo', Map)
      RequestMappingContext context = new RequestMappingContext(requestMappingInfo('/echo'), handlerMethod)

      def settings = new SwaggerGlobalSettings()
      def config = new SpringSwaggerConfig()
      settings.ignorableParameterTypes = config.defaultIgnorableParameterTypes()
      settings.alternateTypeProvider = config.defaultAlternateTypeProvider()
      context.put("swaggerGlobalSettings", settings)

    when:
      ApiModelReader apiModelReader = new ApiModelReader(defaultModelProvider())
      apiModelReader.execute(context)
      Map<String, Object> result = context.getResult()

    then:
      Map<String, Model> models = result.get("models")
      models.size() == 2
      models.containsKey("Entry«string,Pet»")
      models.containsKey("Pet")

  }

  def "Generates the correct models when alternateTypeProvider returns an ingoreable or base parameter type"() {
    given:
      HandlerMethod handlerMethod = handlerMethodIn(BusinessService, 'getResponseEntity', String)
      RequestMappingContext context =
            new RequestMappingContext(requestMappingInfo('/businesses/responseEntity/{businessId}'), handlerMethod)

      def settings = new SwaggerGlobalSettings()
      def config = new SpringSwaggerConfig()
      settings.ignorableParameterTypes = config.defaultIgnorableParameterTypes()
      settings.alternateTypeProvider = config.defaultAlternateTypeProvider()
      def typeResolver = new TypeResolver()
      settings.alternateTypeProvider.addRule(newRule(typeResolver.resolve(ResponseEntity.class, WildcardType.class),
            typeResolver.resolve(WildcardType.class)));
      settings.alternateTypeProvider.addRule(newRule(typeResolver.resolve(HttpEntity.class, WildcardType.class),
            typeResolver.resolve(WildcardType.class)));
      context.put("swaggerGlobalSettings", settings)

    when:
      ApiModelReader apiModelReader = new ApiModelReader(defaultModelProvider())
      apiModelReader.execute(context)
      Map<String, Object> result = context.getResult()

    then:
      Map<String, Model> models = result.get("models")
      models.size() == 0

  }
}
