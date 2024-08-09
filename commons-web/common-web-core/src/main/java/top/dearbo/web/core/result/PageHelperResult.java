package top.dearbo.web.core.result;

import com.github.pagehelper.ISelect;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import top.dearbo.common.base.bean.BaseQuery;
import top.dearbo.common.core.result.PageResult;

import java.util.List;

/**
 * @author 扩展分页结果返回
 * @version 1.0.0
 */
public class PageHelperResult<T> extends PageResult<T> {

	public PageHelperResult(Long total) {
		super(total);
	}

	public PageHelperResult(Long total, List<T> data) {
		super(total, data);
	}

	public static void startPage(BaseQuery paramQuery) {
		PageHelper.startPage(paramQuery.getPageIndex(), paramQuery.getPageSize());
	}

	public static void startPage(Integer pageIndex, Integer pageSize) {
		PageHelper.startPage(pageIndex, pageSize);
	}

	public static <E, T extends BaseQuery> PageInfo<E> getPageInfo(T query, ISelect select) {
		Integer pageIndex = query.getPageIndex();
		Integer pageSize = query.getPageSize();
		boolean count = true;
		if (pageSize.equals(Integer.MAX_VALUE) || pageSize.equals(Integer.MAX_VALUE - 1)) {
			//关闭分页：pageIndex:1,pageSize = Integer.MAX_VALUE
			pageIndex = 1;
			pageSize = Integer.MAX_VALUE;
			count = false;
		}
		String orderBy = getOrderBy(query);
		Page<E> startPage = PageHelper.startPage(pageIndex, pageSize, count);
		if (StringUtils.isNotBlank(orderBy)) {
			startPage.setOrderBy(orderBy);
		}
		PageInfo<E> pageInfo = startPage.doSelectPageInfo(select);
		if (!count) {
			pageInfo.setTotal(pageInfo.getList().size());
		}
		return pageInfo;
	}

	public static <E, T extends BaseQuery> PageResult<E> getPageResult(T query, ISelect select) {
		PageInfo<E> pageInfo = getPageInfo(query, select);
		return new PageHelperResult<>(pageInfo.getTotal(), pageInfo.getList());
	}

	public static <T> PageResult<T> getPageResult(List<T> data) {
		PageInfo<T> pageInfo = new PageInfo<>(data);
		return new PageHelperResult<>(pageInfo.getTotal(), pageInfo.getList());
	}

	public static <T, C> PageResult<C> getPageResultChange(List<T> data, Class<C> targetClass) {
		return getPageResultChange(data, targetClass, null);
	}

	public static <T, C> PageResult<C> getPageResultChange(List<T> data, Class<C> targetClass, Callback<T, C> callback) {
		if (CollectionUtils.isEmpty(data)) {
			return emptyData();
		}
		PageInfo<T> tPageInfo = new PageInfo<>(data);
		return getPageResultChange(tPageInfo.getList(), tPageInfo.getTotal(), targetClass, callback);
	}

	public static <T, C> PageResult<C> getPageResultChange(PageInfo<T> data, Class<C> targetClass) {
		return getPageResultChange(data.getList(), data.getTotal(), targetClass, null);
	}

}
