(function (root) {
    root.FeedbackDataView = function () {
        var me = this;
        var model;
        var username;

        this.initialize = function(assetModel, layer, authorizationPolicy) {
            model = assetModel;
            username = authorizationPolicy.username;
            var selectedAssets = getData();
            renderDialog(selectedAssets, layer);
            bindEvents(layer);
        };

        var bindEvents = function (layer) {
            $('.confirm-modal .cancel').on('click', function() {
                $(':input').val('');
            });
            $('.confirm-modal .save').on('click', function() {
                addSpinner();
             //    collection.send( $(".form-horizontal").serializeArray());
            });
            $(' .confirm-modal .sulje').on('click', function() {
                purge();
            });
        };

        var addSpinner = function () {
            $('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
        };

        var purge = function() {
            $('.confirm-modal').remove();
        };

        var renderDialog = function(selectedAsset, layer) {
            var dialog = createFeedbackForm(selectedAsset, layer);
            $('.container').append(dialog);
        };

        var getData = function(){
           return model.get();
        };

        var getDropdownValue = function(layer){
            var options = $(
                            '<option value="Geometriapalaute">Geometriapalaute </option>'+
                            '<option value="Aineistopalaute">Aineistopalaute</option>'
                           );

           if(layer === 'linkProperty')
               options.find('#feedbackDataType').val('Geometriapalaute');
           else
               options.find('#feedbackDataType').val('Aineistopalaute');

           return options;
        };

        var getLinkIds = function (selectedAsset) {
           return _.map(selectedAsset, function(selected){ return selected.linkId; }).join('');
        };

        var getAssetId = function(selectedAsset, layer){
            if(layer === 'linkProperty')
                return '123456';
            else
                return _.map(selectedAsset, function(selected){ return selected.id; }).join('');
        };

        var userEditableFields = function(){
            return $(
                     '<label class="control-label">Nimi</label>' +
                     '<input type="text" name="name" class="form-control">' +

                     '<label class="control-label">Sähköposti</label>' +
                     '<input type="text" name="email" class="form-control">' +

                     '<label class="control-label">Puhelinnumero</label>' +
                     '<input type="text" name="phoneNumber" id="phoneNumber" class="form-control">');

        };

        var createFeedbackForm = function(selectedAsset, layer) {


           var  dialog =  $('<div class="modal-overlay confirm-modal" id="feedbackData">' +
                        '<div class="modal-dialog">' +
                            '<div class="content">Anna palautetta kohteesta<a class="header-link sulje"">Sulje</a>' + '</div>' +
                            '<form class="form form-horizontal" role="form"">' +
                            '<div class="form-group" id="feedbackForm">' +

                                '<label class="control-label">Linkin id</label>' +
                                '<label id="linkId"></label>'+

                                '<label class="control-label">Kohteen id</label>' +
                                '<label id="assetId"></label>'+

                                '<label class="control-label" id="feedbackType">Palautteen tyyppi</label>' +
                                '<select name="feedbackDataType" id ="feedbackDataType" class="form-control">'+
                                '</select>' +

                                '<label class="control-label">Palaute</label>' +
                                '<textarea name="freeText" id="freetext" class="form-control"></textarea>'+

                                '<label class="control-label">K-tunnus</label>' +
                                '<label id="kidentifier"></label>'+
                            '</div>' +
                            '</form>' +
                            '<div class="actions">' +
                                '<button class = "btn btn-primary save">Lähetä</button>' +
                                '<button class = "btn btn-secondary cancel">Peruuta</button>' +
                            '</div>' +
                        '</div>' +
                    '</div>');

           dialog.find('#feedbackDataType').append(getDropdownValue(layer));
           dialog.find("#kidentifier").append(username);
           dialog.find("#linkId").append(getLinkIds(selectedAsset));
           dialog.find("#assetId").append(getAssetId(selectedAsset, layer));
           dialog.find("#feedbackForm").append(userEditableFields());
           return dialog;
        };
    };
})(this);