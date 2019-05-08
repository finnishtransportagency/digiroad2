(function (root) {
  root.SuggestedAssetsWorkList = function(){
    WorkListView.call(this);
    var me = this;
    this.hrefDir = "#work-list/suggestedAssets";
    this.title = 'Suggested Assets';
    var backend;
    var municipalityName;
    var authorizationPolicy = new AuthorizationPolicy();
    var assetConfig = new AssetTypeConfiguration();

    this.initialize = function(mapBackend) {
      backend = mapBackend;
      me.bindEvents();
    };

    this.bindEvents = function () {
      eventbus.on('suggestedAssets:select', function() {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        me.generateWorkList();
      });
    };

    this.generateWorkList = function() {
      $('#work-list').html('' +
        '<div style="overflow: auto;">' +
        '<div class="page">' +
        '<div class="content-box">' +
        '<header id="work-list-header">' + me.title +
        '<a class="header-link" href="#' + window.applicationModel.getSelectedLayer() + '">Sulje</a>' +
        '</header>' +
        '<div class="work-list">' +
        '</div>' +
        '</div>' +
        '</div>'
      );
    };
  };
})(this);