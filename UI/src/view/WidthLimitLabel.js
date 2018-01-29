(function(root) {

  root.WidthLimitLabel = function(){
    LimitationLabel.call(this);
    var me = this;

    this.getImage = function () {
      return 'images/greenLabeling.png';
    };
  };
})(this);