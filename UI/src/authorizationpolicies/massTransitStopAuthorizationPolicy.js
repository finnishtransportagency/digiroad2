(function(root) {
  root.MassTransitStopAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    function isElyMaintainerOrOperator(municipalityCode) {
      return (me.isElyMaintainer() && me.hasRightsInMunicipality(municipalityCode)) || me.isOperator();
    }

    /**
     * tietojen ylläpitäjä = bus stop maintainer. Return false if user is not operator/busStopMaintainer, meaning that maintainer cannot be changed to ELY-keskus in form unless authorized.
    * */
    this.reduceChoices = function(stopProperty) {
      return stopProperty.publicId == 'tietojen_yllapitaja' && !isElyMaintainerOrOperator(selectedMassTransitStopModel.getRoadLink().getData().municipalityCode);
    };

    /**
     * checks if bus stop is still active and then if user is an operator or ELY-maintainer(operating in permitted area)
    * */
    this.isActiveTrStopWithoutPermission = function(isExpired, isTrStop) {
      return !isExpired && isTrStop && !isElyMaintainerOrOperator(selectedMassTransitStopModel.getMunicipalityCode());
    };

    /** Rules:
    * Municipality maintainer: can update bus stops and other asset types inside own municipalities on admin class 2(municipality) and 3(private)
    * Ely maintainer: can update bus stops and other asset types inside own ELY-area on admin class 1(state) and 2(municipality) and 3(private)
    * Operator: no restrictions
    * */

    this.assetSpecificAccess = function(){
      var municipalityCode = selectedMassTransitStopModel.getRoadLink().getData().municipalityCode;
      return (me.isMunicipalityMaintainer() && !selectedMassTransitStopModel.isAdminClassState() && me.hasRightsInMunicipality(municipalityCode)) ||(me.isElyMaintainer() && me.hasRightsInMunicipality(municipalityCode)) || me.isOperator();
    };

    this.formEditModeAccess = function () {
      if(applicationModel.isReadOnly()){
        return true;
      }

      var properties = selectedMassTransitStopModel.getProperties();

      var owner = _.find(properties, function(property) {
        return property.publicId === "tietojen_yllapitaja"; });

      var condition = typeof owner != 'undefined' && typeof owner.values != 'undefined' &&  !_.isEmpty(owner.values) && _.contains(_.map(owner.values, function (value) {
            return value.propertyValue;
          }), "2");

      var hasAccess = this.assetSpecificAccess();

      eventbus.trigger('application:controlledTR',condition);
      /**boolean inverted because it is used for 'isReadOnly' in mass transit stop form*/
      return !hasAccess;
    };
  };
})(this);