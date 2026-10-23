package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.entity.*

data class ShopCityDraft(
 val shop:ShopEntity,
 val departments:List<ShopDepartmentEntity>,
 val branches:List<ShopBranchEntity>,
 val networkName:String="",
 val purchaseMode:String="inherit",
 val venueEdits:Map<Long,VenueEntity> = emptyMap()
)
