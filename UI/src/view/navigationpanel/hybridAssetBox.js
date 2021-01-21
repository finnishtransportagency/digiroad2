(function (root) {
    root.HybridAssetBox = function (assetConfig) {
        ActionPanelBox.call(this);
        var me = this;
        this.header = function () {
            return assetConfig.title;
        };

        this.title = assetConfig.title;

        this.layerName = assetConfig.layerName;

        this.checkboxPanel = function () {
            return assetConfig.allowComplementaryLinks ? [
                '<div class="panel-section">' +
                '  <div class="check-box-container">' +
                '     <input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
                '   </div>' +
                '</div>'].join('') : '';
        };
        this.predicate = function () {
            return assetConfig.authorizationPolicy.editModeAccess();
        };

        this.municipalityVerified = function () {
            return assetConfig.hasMunicipalityValidation;
        };

        this.toolSelection = new me.ToolSelection([
            new me.Tool('Select', me.selectToolIcon, assetConfig.selectedLinearAsset),
            new me.Tool('Add', me.addToolIcon, assetConfig.selectedPointAsset),
            new me.Tool('Cut', me.cutToolIcon, assetConfig.selectedLinearAsset),
            new me.Tool('Rectangle', me.rectangleToolIcon, assetConfig.selectedLinearAsset),
            new me.Tool('Polygon', me.polygonToolIcon, assetConfig.selectedLinearAsset),
        ]);

        this.editModeToggle = new EditModeToggleButton(me.toolSelection);

        this.template = function () {
            this.expanded = me.elements().expanded;
            me.eventHandler();
            return me.getElement()
                .append(this.expanded)
                .hide();
        };

        this.checkboxPanel = function () {
            return assetConfig.allowComplementaryLinks ? [
                '<div class="panel-section">' +
                '  <div class="check-box-container">' +
                '     <input id="complementaryLinkCheckBox" type="checkbox" /> <lable>Näytä täydentävä geometria</lable>' +
                '   </div>' +
                '</div>'].join('') : '';
        };

        var element = $('<div class="panel-group hybrid-asset ' + _.kebabCase(assetConfig.layerName) + '"/>');

        this.getElement = function () {
            return element;
        };

        this.getShow = function () {
            return show();
        };

        this.getHide = function () {
            return hide();
        };

        this.show = show;
        this.hide = hide;

        function show() {
            if (!assetConfig.authorizationPolicy.editModeAccess()) {
                me.editModeToggle.reset();
            } else {
                me.editModeToggle.toggleEditMode(applicationModel.isReadOnly());
            }
            me.getElement().show();
        }

        function hide() {
            me.getElement().hide();
        }
    };
})(this);