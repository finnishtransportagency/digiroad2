(function (root) {
    root.PointStateRoadExcludeOperatorAuthorizationPolicy = function () {
        AuthorizationPolicy.call(this);
        var me = this;

        this.formEditModeAccess = function (selectedAsset) {
            var isMaintainerAndHaveRights = (me.isMunicipalityMaintainer()) || me.isElyMaintainer() && me.hasRightsInMunicipality(selectedAsset.getMunicipalityCode());

            return me.isStateExclusions(selectedAsset) || (isMaintainerAndHaveRights && !me.isState(selectedAsset.get())) || me.isOperator();
        };

        this.filterRoadLinks = function (roadLink) {
            var isMaintainerAndHaveRights = (me.isMunicipalityMaintainer()) || (me.isElyMaintainer() && roadLink.administrativeClass !== 'Municipality') && me.hasRightsInMunicipality(roadLink.municipalityCode);

            return me.isStateExclusions(roadLink) ||( isMaintainerAndHaveRights && !me.isState(roadLink) ) || me.isOperator();
        };
    };
})(this);