(function(root) {
  root.MassTransitStopAuthorizationPolicy = function() {
    AuthorizationPolicy.call(this);

    var me = this;

    this.isElyMaintainerOrOperator = function(municipalityCode) {
      return (me.isElyMaintainer() && me.hasRightsInMunicipality(municipalityCode)) || me.isOperator();
    };

    this.filterRoadLinks = function(roadLink){
      var isMunicipalityAndHaveRights = me.isMunicipalityMaintainer() && me.hasRightsInMunicipality(roadLink.municipalityCode);
      var isElyAndHaveRights = me.isElyMaintainer() && me.hasRightsInMunicipality(roadLink.municipalityCode);

      return me.isStateExclusions(roadLink) || isMunicipalityAndHaveRights || isElyAndHaveRights || me.isOperator();
    };

    /**
     * tietojen ylläpitäjä = bus stop maintainer. Return false if user is not operator/elyMaintainer, meaning that maintainer cannot be changed to ELY-keskus in form unless authorized.
    * */
    this.reduceChoices = function(stopProperty) {
      var municipalityCode = selectedMassTransitStopModel.getMunicipalityCode();
      return stopProperty.publicId == 'tietojen_yllapitaja' && !me.isElyMaintainerOrOperator(municipalityCode);
    };

    /**
     * checks if bus stop is still active and then if user is an operator or ELY-maintainer(operating in permitted area)
    * */
    this.isActiveTrStopWithoutPermission = function(isExpired, isTrStop) {
      var municipalityCode = selectedMassTransitStopModel.getMunicipalityCode();
      return !isExpired && isTrStop && !me.isElyMaintainerOrOperator(municipalityCode);
    };

    /** Rules:
    * Municipality maintainer: can update bus stops and other asset types inside own municipalities on admin class 2(municipality) and 3(private)
    * Ely maintainer: can update bus stops and other asset types inside own ELY-area on admin class 1(state) and 2(municipality) and 3(private)
    * Operator: no restrictions
    * */

    this.assetSpecificAccess = function(){
      var municipalityCode = selectedMassTransitStopModel.getMunicipalityCode();

      var isMunicipalityAndHaveRights = me.isMunicipalityMaintainer() && me.hasRightsInMunicipality(municipalityCode);
      var isElyyAndHaveRights = me.isElyMaintainer() && me.hasRightsInMunicipality(municipalityCode);

      return me.isStateExclusions(selectedMassTransitStopModel) || ( isMunicipalityAndHaveRights || isElyyAndHaveRights || me.isOperator() );
    };


    this.formEditModeAccess = function () {
      if(applicationModel.isReadOnly()){
        return true;
      }

      var properties = selectedMassTransitStopModel.getProperties();

      var owner = _.find(properties, function(property) {
        return property.publicId === "tietojen_yllapitaja"; });

      var condition = typeof owner != 'undefined' && typeof owner.values != 'undefined' &&  !_.isEmpty(owner.values) && _.includes(_.map(owner.values, function (value) {
            return value.propertyValue;
          }), "2");

      var hasLivi = _.find(properties, function(property) {
          return property.publicId === 'yllapitajan_koodi'; });

      var liviCondition = typeof hasLivi != 'undefined' && typeof hasLivi.values != 'undefined' &&  !_.isEmpty(hasLivi.values);

      var hasAccess = this.assetSpecificAccess();

      eventbus.trigger('application:controlledTR', (condition && liviCondition));
      /**boolean inverted because it is used for 'isReadOnly' in mass transit stop form*/
      return !hasAccess;
    };

    this.handleMassTransitStopSuggestion = function(selectedAsset, suggestedProperty) {
      var suggestedBoxValue = !_.isEmpty(suggestedProperty.values) ? !!parseInt(suggestedProperty.values[0].propertyValue) : false;
      return (!_.isNumber(selectedAsset.getId()) && me.isOperator()) || (suggestedBoxValue && (me.isOperator() || me.isMunicipalityMaintainer()));
    };
  };
})(this);